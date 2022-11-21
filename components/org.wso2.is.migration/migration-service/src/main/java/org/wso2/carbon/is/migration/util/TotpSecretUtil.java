/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.is.migration.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.identity.core.migrate.MigrationClientException;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.is.migration.internal.ISMigrationServiceDataHolder;
import org.wso2.carbon.is.migration.service.Migrator;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.api.UserStoreManager;
import org.wso2.carbon.user.core.common.AbstractUserStoreManager;
import org.wso2.carbon.user.core.jdbc.JDBCUserStoreManager;
import org.wso2.carbon.user.core.ldap.ReadWriteLDAPUserStoreManager;
import org.wso2.carbon.user.core.model.ExpressionAttribute;
import org.wso2.carbon.user.core.model.ExpressionCondition;
import org.wso2.carbon.user.core.model.ExpressionOperation;
import org.wso2.carbon.user.core.service.RealmService;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.wso2.carbon.is.migration.util.Constant.INCREMENT_PARAMETER_NAME;
import static org.wso2.carbon.is.migration.util.Constant.MIGRATE_ALL;
import static org.wso2.carbon.is.migration.util.Constant.MIGRATING_DOMAINS;
import static org.wso2.carbon.is.migration.util.Constant.STARTING_POINT_PARAMETER_NAME;
import static org.wso2.carbon.is.migration.util.Constant.TOTP_SECRET_KEY_CLAIM;
import static org.wso2.carbon.is.migration.util.Constant.TOTP_VERIFIED_SECRET_KEY_CLAIM;
import static org.wso2.carbon.is.migration.util.UserStoreOperationsUtil.getAllTotpSecretDataFromDb;
import static org.wso2.carbon.is.migration.util.UserStoreOperationsUtil.getSecretKeyClaimValue;
import static org.wso2.carbon.is.migration.util.UserStoreOperationsUtil.updateNewTotpSecretsToDb;
import static org.wso2.carbon.is.migration.util.UserStoreOperationsUtil.updateTotpSecretClaim;

public class TotpSecretUtil {

    private static final Logger log = LoggerFactory.getLogger(TotpSecretUtil.class);
    private static final String USER_OPERATION_EVENT_LISTENER_TYPE = "org.wso2.carbon.user.core.listener" +
            ".UserOperationEventListener";
    private static final String USER_OPERATION_EVENT_LISTENER =
            "org.wso2.carbon.identity.governance.listener.IdentityStoreEventListener";
    private static final String DATA_STORE_PROPERTY_NAME = "Data.Store";
    private static final String USER_STORE_BASED_IDENTITY_STORE =
            "org.wso2.carbon.identity.governance.store.UserStoreBasedIdentityDataStore";

    private static RealmService realmService = ISMigrationServiceDataHolder.getRealmService();
    private static int increment = 100;
    private static int offset = 0;
    private static final String DEFAULT_PROFILE = "default";
    private static final int DEFAULT_CHUNK_SIZE = 10000;

    public static void migrateTotpSecretKeys(int chunkSize, Migrator migrator) throws MigrationClientException {

        String storeClassName = getIdentityClaimStoreClassname();
        try {
            if (USER_STORE_BASED_IDENTITY_STORE.equals(storeClassName)) {

                migrateWithUserstoreBasedIdentityStore(chunkSize, migrator);

            } else {
                migrateWithJDBCIdentityStore(chunkSize, migrator);
            }
        } catch (MigrationClientException e) {
            if (migrator.isContinueOnError()) {
                log.error("Error while migrating TOTP secret keys.");
            } else {
                throw new MigrationClientException("Error while migrating TOTP secret keys.", e);
            }
        }

    }

    private static void migrateWithJDBCIdentityStore(int chunkSize, Migrator migrator) throws MigrationClientException {

        int offSet = 0;
        while (true) {
            List<TotpSecretData> totpSecretDataList = getTotpSecretKeyDataList(chunkSize, offSet, migrator);
            List<TotpSecretData> updatedTotpSecretDataList =
                    transformPasswordFromOldToNewEncryption(totpSecretDataList);
            updateSecretKeyClaimDataSet(migrator, updatedTotpSecretDataList);
            offSet += totpSecretDataList.size();
            if (totpSecretDataList.isEmpty()) {
                break;
            }
        }
    }

    private static void migrateWithUserstoreBasedIdentityStore(int chunkSize, Migrator migrator)
            throws MigrationClientException {

        int userCounter = 0;
        String tenantDomain = null;
        String userStoreDomain = null;
        try {

            Properties migrationProperties = migrator.getMigratorConfig().getParameters();
            if (migrationProperties.containsKey(MIGRATE_ALL) && ((Boolean) migrationProperties.get(MIGRATE_ALL))) {
                Tenant[] tenants = UserStoreOperationsUtil.getAllTenants();

                for (Tenant tenant : tenants) {
                    // Ignore inactive tenants.
                    if (!tenant.isActive()) {
                        continue;
                    }
                    tenantDomain = tenant.getDomain();
                    log.info("Migration started for tenant domain: {}", tenantDomain);
                    int tenantId = realmService.getTenantManager().getTenantId(tenantDomain);
                    if (tenantId == -1) {
                        log.error("Invalid tenant domain name '{}' provided.", tenantDomain);
                        throw new MigrationClientException("Invalid tenant domain provided.");
                    }
                    Integer startingPointValue = (Integer) migrationProperties.get(STARTING_POINT_PARAMETER_NAME);
                    Integer incrementValue = (Integer) migrationProperties.get(INCREMENT_PARAMETER_NAME);
                    String migratingDomains = (String) migrationProperties.get(MIGRATING_DOMAINS);
                    if (startingPointValue != null) {
                        offset = startingPointValue;
                    }

                    if (incrementValue != null) {
                        increment = incrementValue;
                    }

                    log.info("TOTP secret migrator started with offset {} and increment {} .", offset, increment);

                    UserRealm userRealm = realmService.getTenantUserRealm(tenantId);
                    UserStoreManager userStoreManager = userRealm.getUserStoreManager();

                    String[] userstoreDomains;
                    if (migratingDomains != null) {
                        userstoreDomains = migratingDomains.split(",");
                    } else {
                        userstoreDomains =
                                UserStoreOperationsUtil.getAllDomainNames((AbstractUserStoreManager) userStoreManager);
                    }

                    for (String domain : userstoreDomains) {
                        userStoreDomain = domain;
                        AbstractUserStoreManager abstractUserStoreManager =
                                (AbstractUserStoreManager) ((AbstractUserStoreManager) userStoreManager)
                                        .getSecondaryUserStoreManager(domain);

                        if (abstractUserStoreManager == null) {
                            log.error("Invalid domain name {} provided. No user store found for the given domain name.",
                                    userstoreDomains);
                            throw new MigrationClientException("Invalid domain name provided. No user store found.");
                        }
                        log.info("Migration started for domain: {}", domain);
                        if (abstractUserStoreManager instanceof JDBCUserStoreManager) {

                            int offSet = 0;
                            log.info("{} Migration starting on UM_USER_ATTRIBUTE table with offset {} and limit {}.",
                                    Constant.MIGRATION_LOG, offset, chunkSize);

                            String secretKeyMappedAttribute =
                                    abstractUserStoreManager.getClaimManager().getAttributeName(domain,
                                            TOTP_SECRET_KEY_CLAIM);
                            String verifiedSecretKeyMappedAttribute =
                                    abstractUserStoreManager.getClaimManager().getAttributeName(domain,
                                            TOTP_VERIFIED_SECRET_KEY_CLAIM);
                            while (true) {
                                List<TotpSecretData> totpSecretDataList =
                                        getTotpSecretKeyDataListFromJdbcUserstore(chunkSize, offSet, migrator,
                                                secretKeyMappedAttribute, verifiedSecretKeyMappedAttribute);
                                List<TotpSecretData> updatedTotpSecretDataList =
                                        transformPasswordFromOldToNewEncryption(totpSecretDataList);
                                updateSecretKeyClaimDataSetJdbcUserStore(migrator, updatedTotpSecretDataList);
                                offSet += totpSecretDataList.size();
                                if (totpSecretDataList.isEmpty()) {
                                    break;
                                }
                            }

                        }

                        ExpressionCondition expressionCondition =
                                new ExpressionCondition(ExpressionOperation.SW.toString(),
                                        ExpressionAttribute.USERNAME.toString(), "");
                        userCounter = offset;

                        while (true) {
                            String[] userList = abstractUserStoreManager.getUserList(expressionCondition, domain,
                                    DEFAULT_PROFILE, increment, userCounter, "", "");
                            log.info("Migrating users from offset {} to increment of {}.", userCounter, increment);
                            for (String username : userList) {
                                if (log.isDebugEnabled()) {
                                    log.debug("Migrating user {}, counter index {}", username, userCounter);
                                }

                                if (abstractUserStoreManager instanceof ReadWriteLDAPUserStoreManager) {
                                    String secretKeyClaimValue = getSecretKeyClaimValue(username, TOTP_SECRET_KEY_CLAIM,
                                            abstractUserStoreManager);
                                    String verifiedSecretKeyClaimValue = getSecretKeyClaimValue(username,
                                            TOTP_VERIFIED_SECRET_KEY_CLAIM, abstractUserStoreManager);
                                    String newlyEncryptedSecretKeyValue =
                                            EncryptionUtil.transformToSymmetric(secretKeyClaimValue);
                                    String newlyEncryptedVerifiedSecretKeyValue =
                                            EncryptionUtil.transformToSymmetric(verifiedSecretKeyClaimValue);
                                    updateTotpSecretClaim(username, newlyEncryptedSecretKeyValue,
                                            abstractUserStoreManager);
                                    updateTotpSecretClaim(username, newlyEncryptedVerifiedSecretKeyValue,
                                            abstractUserStoreManager);
                                }

                                userCounter++;
                            }
                            if (userList.length < increment) {
                                break;
                            }
                        }
                    }
                }
            }
            log.info("TOTP secret key migration for userstore based data store completed.");
        } catch (UserStoreException e) {
            String message = String.format("Error occurred while updating user id for the user. user id updating " +
                            "process stopped at the offset %d in domain %s in tenant %s", userCounter, userStoreDomain,
                    tenantDomain);
            log.error(message, e);
            throw new MigrationClientException(message, e);
        }

    }

    private static List<TotpSecretData> getTotpSecretKeyDataList(int chunkSize, int offSet, Migrator migrator)
            throws MigrationClientException {

        List<TotpSecretData> totpSecretDataList;
        try (Connection connection = migrator.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            totpSecretDataList = IdentityClaimDAO.getInstance().getAllTotpSecretData(chunkSize, offSet, connection);
        } catch (SQLException e) {
            throw new MigrationClientException("Error while retrieving database connection for identity claims " +
                    "database. ", e);
        }
        return totpSecretDataList;
    }

    private static List<TotpSecretData> getTotpSecretKeyDataListFromJdbcUserstore(int limit,
                                                                                  int offset, Migrator migrator,
                                                                                  String secretKeyMappedAttr,
                                                                                  String verifiedSecretKeyMappedAttribute)
            throws MigrationClientException {

        List<TotpSecretData> totpSecretDataList;
        try (Connection connection = migrator.getDataSource(Schema.UM.getName()).getConnection()) {
            connection.setAutoCommit(false);
            totpSecretDataList = getAllTotpSecretDataFromDb(limit, offset, connection, secretKeyMappedAttr,
                    verifiedSecretKeyMappedAttribute);
        } catch (SQLException e) {
            throw new MigrationClientException("Error while retrieving database connection for identity claims from " +
                    "user management database. ", e);
        }
        return totpSecretDataList;
    }

    private static List<TotpSecretData> transformPasswordFromOldToNewEncryption(List<TotpSecretData> totpSecretDataList)
            throws MigrationClientException {

        List<TotpSecretData> updatedTotpSecretDataList = new ArrayList<>();

        for (TotpSecretData totpSecretData : totpSecretDataList) {
            String newEncryptedPassword =
                    EncryptionUtil.transformToSymmetric(totpSecretData.getEncryptedSeceretkeyValue());
            TotpSecretData updatedTotpSecretData = new TotpSecretData(totpSecretData.getTenantId(),
                    totpSecretData.getUserName(), newEncryptedPassword, totpSecretData.getDataKey());
            updatedTotpSecretDataList.add(updatedTotpSecretData);
        }

        return updatedTotpSecretDataList;
    }

    public static void updateSecretKeyClaimDataSet(Migrator migrator, List<TotpSecretData> updatedTotpSecretDataList)
            throws MigrationClientException {

        try (Connection connection = migrator.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            IdentityClaimDAO.getInstance().updateNewTotpSecrets(updatedTotpSecretDataList, connection);
        } catch (SQLException e) {
            throw new MigrationClientException("", e);
        }
    }

    public static void updateSecretKeyClaimDataSetJdbcUserStore(Migrator migrator,
                                                                List<TotpSecretData> updatedTotpSecretDataList)
            throws MigrationClientException {

        try (Connection connection = migrator.getDataSource(Schema.UM.getName()).getConnection()) {
            connection.setAutoCommit(false);
            updateNewTotpSecretsToDb(updatedTotpSecretDataList, connection);
        } catch (SQLException e) {
            throw new MigrationClientException("", e);
        }
    }

    private static String getIdentityClaimStoreClassname() {

        return IdentityUtil.readEventListenerProperty(USER_OPERATION_EVENT_LISTENER_TYPE,
                USER_OPERATION_EVENT_LISTENER).getProperties().get(DATA_STORE_PROPERTY_NAME).toString();

    }
}
