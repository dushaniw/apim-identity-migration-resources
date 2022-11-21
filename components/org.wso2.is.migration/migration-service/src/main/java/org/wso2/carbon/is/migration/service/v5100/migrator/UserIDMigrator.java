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

package org.wso2.carbon.is.migration.service.v5100.migrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.identity.core.migrate.MigrationClientException;
import org.wso2.carbon.is.migration.internal.ISMigrationServiceDataHolder;
import org.wso2.carbon.is.migration.service.Migrator;
import org.wso2.carbon.is.migration.util.ReportUtil;
import org.wso2.carbon.is.migration.util.Schema;
import org.wso2.carbon.is.migration.util.Utility;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.api.UserStoreManager;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.common.AbstractUserStoreManager;
import org.wso2.carbon.user.core.jdbc.JDBCUserStoreManager;
import org.wso2.carbon.user.core.jdbc.UniqueIDJDBCUserStoreManager;
import org.wso2.carbon.user.core.ldap.ActiveDirectoryUserStoreManager;
import org.wso2.carbon.user.core.ldap.ReadOnlyLDAPUserStoreManager;
import org.wso2.carbon.user.core.ldap.ReadWriteLDAPUserStoreManager;
import org.wso2.carbon.user.core.ldap.UniqueIDActiveDirectoryUserStoreManager;
import org.wso2.carbon.user.core.ldap.UniqueIDReadOnlyLDAPUserStoreManager;
import org.wso2.carbon.user.core.ldap.UniqueIDReadWriteLDAPUserStoreManager;
import org.wso2.carbon.user.core.model.ExpressionAttribute;
import org.wso2.carbon.user.core.model.ExpressionCondition;
import org.wso2.carbon.user.core.model.ExpressionOperation;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.user.core.util.UserCoreUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.wso2.carbon.is.migration.util.Constant.FORCE_UPDATE_USER_ID;
import static org.wso2.carbon.is.migration.util.Constant.INCREMENT_PARAMETER_NAME;
import static org.wso2.carbon.is.migration.util.Constant.MIGRATE_ALL;
import static org.wso2.carbon.is.migration.util.Constant.MIGRATING_DOMAINS;
import static org.wso2.carbon.is.migration.util.Constant.REPORT_PATH;
import static org.wso2.carbon.is.migration.util.Constant.STARTING_POINT_PARAMETER_NAME;
import static org.wso2.carbon.is.migration.util.Constant.TENANT_DOMAIN;
import static org.wso2.carbon.is.migration.util.Constant.USERNAME_CLAIM;
import static org.wso2.carbon.is.migration.util.Constant.USER_ID_CLAIM;

/**
 * UserIDMigrator.
 */
public class UserIDMigrator extends Migrator {

    private static final Logger log = LoggerFactory.getLogger(UserIDMigrator.class);
    private static final String UPDATE_USER_ID_SQL =
            "UPDATE UM_USER " +
                    "SET UM_USER_ID = ? " +
                    "WHERE UM_USER_NAME = ? AND UM_TENANT_ID = ?";
    private static final String GET_USER_ID =
            "SELECT UM_USER_ID " +
                    "FROM UM_USER " +
                    "WHERE UM_USER_NAME = ? AND UM_TENANT_ID = ?";
    private static final String DEFAULT_PROFILE = "default";
    private int increment = 100;
    private int offset = 0;
    private RealmService realmService = ISMigrationServiceDataHolder.getRealmService();
    private ReportUtil reportUtil;
    private int numberOfDomains;
    private int numberOfTenants;
    private int numberOfWarnings;

    @Override
    public void migrate() throws MigrationClientException {

        // Counter to the updated user in each user store in each tenant.
        int userCounter = 0;
        String tenantDomain = null;
        String userStoreDomain = null;

        try {
            Properties migrationProperties = getMigratorConfig().getParameters();

            // If migrate all property is there, we have to migrate all the tenants.
            if (migrationProperties.containsKey(MIGRATE_ALL) && ((Boolean) migrationProperties.get(MIGRATE_ALL))) {
                Tenant[] tenants = getAllTenants();
                // Clear all other properties before we add our ones since we don't need other properties.
                migrationProperties.clear();
                for (Tenant tenant : tenants) {
                    // Ignore inactive tenants.
                    if (!tenant.isActive()) {
                        continue;
                    }
                    HashMap<String, Object> parameters = new HashMap<>();
                    parameters.put(STARTING_POINT_PARAMETER_NAME, migrationProperties
                            .get(STARTING_POINT_PARAMETER_NAME));
                    parameters.put(INCREMENT_PARAMETER_NAME, migrationProperties.get(INCREMENT_PARAMETER_NAME));
                    parameters.put(MIGRATING_DOMAINS, migrationProperties.get(MIGRATING_DOMAINS));
                    parameters.put(FORCE_UPDATE_USER_ID, migrationProperties.get(FORCE_UPDATE_USER_ID));
                    parameters.put(TENANT_DOMAIN, tenant.getDomain());
                    migrationProperties.put(tenant.getDomain(), parameters);
                }
            }

            // Migration properties are provided tenant wise.
            for (Object key : migrationProperties.keySet()) {
                String keyStr = (String) key;
                HashMap<String, Object> parameters = (HashMap<String, Object>) migrationProperties.get(keyStr);

                // Get the registered tenant domains from the config.
                tenantDomain = (String) parameters.get(TENANT_DOMAIN);
                log.info("Migration started for tenant domain: {}", tenantDomain);

                // Get the related domain from our map to resolve the id.
                int tenantId = realmService.getTenantManager().getTenantId(tenantDomain);
                if (tenantId == -1) {
                    log.error("Invalid tenant domain name '{}' provided.", tenantDomain);
                    throw new MigrationClientException("Invalid tenant domain provided.");
                }

                // Starting point of the user migration.
                Integer startingPointValue = (Integer) parameters.get(STARTING_POINT_PARAMETER_NAME);
                // Chunk size to be retrieved from the user store.
                Integer incrementValue = (Integer) parameters.get(INCREMENT_PARAMETER_NAME);
                // Domains need to be migrated.
                String migratingDomains = (String) parameters.get(MIGRATING_DOMAINS);
                // Forcefully update the user id even if there is already a value.
                Boolean forceUpdateUserId = (Boolean) parameters.get(FORCE_UPDATE_USER_ID);

                if (startingPointValue != null) {
                    offset = startingPointValue;
                }

                if (incrementValue != null) {
                    increment = incrementValue;
                }

                if (forceUpdateUserId == null) {
                    forceUpdateUserId = false;
                }

                log.info("User ID migrator started with offset {} and increment {} .", offset, increment);

                UserRealm userRealm = realmService.getTenantUserRealm(tenantId);
                UserStoreManager userStoreManager = userRealm.getUserStoreManager();

                // If the domains are provided in config, use them. Otherwise update all the available domains.
                String[] domains;
                if (migratingDomains != null) {
                    if (migratingDomains.isEmpty()) {
                        domains = new String[0];
                        log.info("User store domain list is empty");
                    } else {
                        domains = migratingDomains.split(",");
                    }
                } else {
                    domains = getAllDomainNames((AbstractUserStoreManager) userStoreManager);
                }

                // Iterate through provided domains for the given tenant.
                for (String domain : domains) {
                    userStoreDomain = domain;
                    AbstractUserStoreManager abstractUserStoreManager =
                            (AbstractUserStoreManager) ((AbstractUserStoreManager) userStoreManager)
                                    .getSecondaryUserStoreManager(domain);

                    if (abstractUserStoreManager == null) {
                        log.error("Invalid domain name {} provided. No user store found for the given domain name.",
                                domain);
                        throw new MigrationClientException("Invalid domain name provided. No user store found.");
                    }

                    log.info("Migration started for domain: {}", domain);
                    ExpressionCondition expressionCondition = new ExpressionCondition(ExpressionOperation.SW.toString(),
                            ExpressionAttribute.USERNAME.toString(), "");

                    userCounter = offset;
                    // Iterate for each increment.
                    while (true) {
                        // Get set of users according to the given increment value.
                        String[] userList = abstractUserStoreManager.getUserList(expressionCondition, domain,
                                DEFAULT_PROFILE, increment, userCounter, "", "");
                        log.info("Migrating users from offset {} to increment of {}.", userCounter, increment);

                        // Iterate for each user.
                        for (String username : userList) {
                            username = UserCoreUtil.removeDomainFromName(username);
                            if (log.isDebugEnabled()) {
                                log.debug("Migrating user {}, counter index {}", username, userCounter);
                            }

                            // JDBC User stores.
                            if (abstractUserStoreManager instanceof JDBCUserStoreManager) {
                                // If this is an JDBC user store and SCIM enabled, get the SCIM user id and add it as
                                // the unique user id. In non SCIM enabled scenario's, user id is generated by SQL
                                // script.
                                if (isSCIMEnabled(abstractUserStoreManager) &&
                                        !isCustomUserStore(abstractUserStoreManager)) {
                                    // If this is not a custom user store, we can update the user id column as well.
                                    String userId = getSCIMIDClaimValue(username, abstractUserStoreManager);

                                    if (userId != null && !userId.isEmpty()) {
                                        updateUserIdColumn(userId, username, tenantId);
                                    } else {
                                        // If the user did not have a SCIM ID, get the SQL generated UUID
                                        // and update user id claim
                                        userId = getUserIDClaimFromDB(username, tenantId);
                                        updateUserIDClaim(username, userId, abstractUserStoreManager,
                                                forceUpdateUserId);
                                    }
                                } else {
                                    // In this scenario, we have generated the UUID using SQL. So we have to get it
                                    // and add it as the user id claim.
                                    String userId = getUserIDClaimFromDB(username, tenantId);
                                    updateUserIDClaim(username, userId, abstractUserStoreManager,
                                            forceUpdateUserId);
                                }
                            }

                            // If this is a LDAP user store, generate and update the user id.
                            if (abstractUserStoreManager instanceof ReadWriteLDAPUserStoreManager &&
                                    !isSCIMEnabled(abstractUserStoreManager)) {
                                String uuid = UUID.randomUUID().toString();
                                updateUserIDClaim(username, uuid, abstractUserStoreManager, forceUpdateUserId);
                            }

                            // We need the username value in the username claim. This is for all scenarios.
                            updateUserNameClaim(username, abstractUserStoreManager);
                            userCounter++;
                        }
                        if (userList.length < increment) {
                            break;
                        }
                    }
                }
            }
            log.info("User id migration completed.");
        } catch (UserStoreException | SQLException e) {
            String message = String.format("Error occurred while updating user id for the user. user id updating " +
                            "process stopped at the offset %d in domain %s in tenant %s", userCounter, userStoreDomain,
                    tenantDomain);
            log.error(message, e);
            throw new MigrationClientException(message, e);
        }
    }

    public void dryRun() throws MigrationClientException {

        log.info("Executing dry run for {}", this.getClass().getName());

        Properties migrationProperties = getMigratorConfig().getParameters();
        String reportPath = (String) migrationProperties.get(REPORT_PATH);

        try {
            reportUtil = new ReportUtil(reportPath);
            Tenant[] tenants = getAllTenants();
            for (Tenant tenant : tenants) {
                try {
                    validateForTenant(tenant);
                } catch (UserStoreException e) {
                    throw new MigrationClientException("Error occurred while running the dry run.", e);
                }
            }
            reportUtil.writeMessage("\n--- Summary of the report ---\n");
            reportUtil.writeMessage(String.format("Number of tenants: %d \nNumber of domains: %d \n" +
                    "Number of warnings: %d ", numberOfTenants, numberOfDomains, numberOfWarnings));
            reportUtil.commit();
        } catch (IOException ex) {
            throw new MigrationClientException("Error while writing the dry run report.", ex);
        }
    }

    private void validateForTenant(Tenant tenant) throws UserStoreException {

        UserRealm userRealm = realmService.getTenantUserRealm(tenant.getId());
        UserStoreManager userStoreManager = userRealm.getUserStoreManager();
        String[] domains = getAllDomainNames((AbstractUserStoreManager) userStoreManager);
        for (String domain : domains) {
            AbstractUserStoreManager abstractUserStoreManager = (AbstractUserStoreManager)
                    ((AbstractUserStoreManager) userStoreManager).getSecondaryUserStoreManager(domain);
            validateForDomain(tenant.getDomain(), domain, abstractUserStoreManager);
        }
        numberOfTenants++;
    }

    private void validateForDomain(String tenant, String domain, AbstractUserStoreManager userStoreManager) {

        String type = userStoreManager.getClass().getName();
        String scimEnabled = String.valueOf(userStoreManager.isSCIMEnabled());
        String tag = "Info: ";

        String suggestion = "";
        if (userStoreManager instanceof ReadOnlyLDAPUserStoreManager && userStoreManager.isSCIMEnabled()) {
            tag = "WARN: ";
            suggestion = "This user store has SCIM enabled hence migration not required. Please update the user id " +
                    "attribute in the user store with attribute used for the SCIM.";
            numberOfWarnings++;
        } else if (isCustomUserStore(userStoreManager)) {
            tag = "WARN: ";
            suggestion = "This is a custom user. Only user id claim will be updated. Hence performance degradation " +
                    "can be expected. If this is JDBC, 'user_id' column is expected.";
            numberOfWarnings++;
        }

        String message = String.format("%s Tenant domain: %s | User Store domain: %s | Type: %s | SCIM Enabled: %s | " +
                "Suggestion: %s", tag, tenant, domain, type, scimEnabled, suggestion);
        reportUtil.writeMessage(message);
        numberOfDomains++;
    }

    private void updateUserIDClaim(String username, String userId, AbstractUserStoreManager abstractUserStoreManager,
                                   boolean forceUpdateUserId)
            throws org.wso2.carbon.user.core.UserStoreException {

        String value = abstractUserStoreManager.getUserClaimValue(username, USER_ID_CLAIM, DEFAULT_PROFILE);
        if (!forceUpdateUserId && value != null && !value.isEmpty()) {
            return;
        }

        Map<String, String> claimValues = new HashMap<String, String>(){{
            put(USER_ID_CLAIM, userId);
        }};
        abstractUserStoreManager.doSetUserClaimValues(username, claimValues, DEFAULT_PROFILE);
    }

    private String getSCIMIDClaimValue(String username, AbstractUserStoreManager abstractUserStoreManager)
            throws org.wso2.carbon.user.core.UserStoreException {

        return abstractUserStoreManager.getUserClaimValue(username, USER_ID_CLAIM, DEFAULT_PROFILE);
    }

    private void updateUserIdColumn(String userId, String username, int tenantId)
            throws MigrationClientException, SQLException {

        try (Connection connection = getDataSource(Schema.UM.getName()).getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_USER_ID_SQL);
            preparedStatement.setString(1, userId);
            preparedStatement.setString(2, username);
            preparedStatement.setInt(3, tenantId);
            preparedStatement.executeUpdate();
        }
    }

    private void updateUserNameClaim(String username, AbstractUserStoreManager abstractUserStoreManager)
            throws org.wso2.carbon.user.core.UserStoreException {

        // If we don't have a value for username, we need to update it.
        String value = abstractUserStoreManager.getUserClaimValue(username, USERNAME_CLAIM, DEFAULT_PROFILE);
        if (value != null && !value.isEmpty()) {
            return;
        }

        Map<String, String> claimValues = new HashMap<String, String>(){{
            put(USERNAME_CLAIM, username);
        }};
        abstractUserStoreManager.doSetUserClaimValues(username, claimValues, DEFAULT_PROFILE);
    }

    private String[] getAllDomainNames(AbstractUserStoreManager abstractUserStoreManager) {

        List<String> domainNames = new ArrayList<>();

        // Add the primary domain.
        domainNames.add(UserCoreUtil.getDomainName(abstractUserStoreManager.getRealmConfiguration()));

        // Add the rest if there are any.
        while (true) {
            AbstractUserStoreManager userStoreManager = (AbstractUserStoreManager) abstractUserStoreManager
                    .getSecondaryUserStoreManager();
            if (userStoreManager == null) {
                break;
            }
            domainNames.add(UserCoreUtil.getDomainName(userStoreManager.getRealmConfiguration()));
            abstractUserStoreManager = userStoreManager;
        }
        return domainNames.toArray(new String[0]);
    }

    private Tenant[] getAllTenants() throws MigrationClientException {

        // Add the super tenant.
        Tenant superTenant = new Tenant();
        superTenant.setDomain("carbon.super");
        superTenant.setId(-1234);
        superTenant.setActive(true);

        // Add the rest.
        Set<Tenant> tenants = Utility.getTenants();
        tenants.add(superTenant);

        return tenants.toArray(new Tenant[0]);
    }

    private String getUserIDClaimFromDB(String username, int tenantId) throws MigrationClientException, SQLException {

        try (Connection connection = getDataSource(Schema.UM.getName()).getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(GET_USER_ID);
            preparedStatement.setString(1, username);
            preparedStatement.setInt(2, tenantId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("UM_USER_ID");
                }
            }
        }
        return null;
    }

    private boolean isCustomUserStore(UserStoreManager userStoreManager) {

        Class clazz = userStoreManager.getClass();

        return !JDBCUserStoreManager.class.equals(clazz)
                && !ReadOnlyLDAPUserStoreManager.class.equals(clazz)
                && !ReadWriteLDAPUserStoreManager.class.equals(clazz)
                && !ActiveDirectoryUserStoreManager.class.equals(clazz)
                && !UniqueIDJDBCUserStoreManager.class.equals(clazz)
                && !UniqueIDReadOnlyLDAPUserStoreManager.class.equals(clazz)
                && !UniqueIDReadWriteLDAPUserStoreManager.class.equals(clazz)
                && !UniqueIDActiveDirectoryUserStoreManager.class.equals(clazz);
    }

    private boolean isSCIMEnabled(AbstractUserStoreManager abstractUserStoreManager) {

        String scimEnabled = abstractUserStoreManager.getRealmConfiguration()
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_SCIM_ENABLED);
        if (scimEnabled != null) {
            return Boolean.parseBoolean(scimEnabled);
        } else {
            return false;
        }
    }
}
