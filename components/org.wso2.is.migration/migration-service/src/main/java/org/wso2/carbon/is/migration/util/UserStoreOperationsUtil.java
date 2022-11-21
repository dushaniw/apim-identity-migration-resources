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

import org.apache.commons.lang.StringUtils;
import org.wso2.carbon.identity.core.migrate.MigrationClientException;
import org.wso2.carbon.is.migration.service.Migrator;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreManager;
import org.wso2.carbon.user.core.common.AbstractUserStoreManager;
import org.wso2.carbon.user.core.jdbc.JDBCUserStoreManager;
import org.wso2.carbon.user.core.jdbc.UniqueIDJDBCUserStoreManager;
import org.wso2.carbon.user.core.ldap.ActiveDirectoryUserStoreManager;
import org.wso2.carbon.user.core.ldap.ReadOnlyLDAPUserStoreManager;
import org.wso2.carbon.user.core.ldap.ReadWriteLDAPUserStoreManager;
import org.wso2.carbon.user.core.ldap.UniqueIDActiveDirectoryUserStoreManager;
import org.wso2.carbon.user.core.ldap.UniqueIDReadOnlyLDAPUserStoreManager;
import org.wso2.carbon.user.core.ldap.UniqueIDReadWriteLDAPUserStoreManager;
import org.wso2.carbon.user.core.util.UserCoreUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.wso2.carbon.is.migration.util.Constant.TOTP_SECRET_KEY_CLAIM;
import static org.wso2.carbon.is.migration.util.SQLConstants.RETRIEVE_PAGINATED_TOTP_SECRET_KEY_CLAIM_DATA_FROM_JDBC_USERSTORE_WITH_MYSQL;
import static org.wso2.carbon.is.migration.util.SQLConstants.RETRIEVE_PAGINATED_TOTP_SECRET_KEY_CLAIM_DATA_FROM_JDBC_USERSTORE_WITH_OTHER;
import static org.wso2.carbon.is.migration.util.SQLConstants.UPDATE_TOTP_SECRET_TO_JDBC_USERSTORE;
import static org.wso2.carbon.user.core.UserCoreConstants.DEFAULT_PROFILE;

public class UserStoreOperationsUtil {

    private static final String UPDATE_SECRET_KEY_SQL =
            "UPDATE UM_USER_ATTRIBUTE " +
                    "SET  UM_ATTR_VALUE   = ? " +
                    "WHERE UM_USER_NAME = ? AND UM_TENANT_ID = ? AND UM_ATTR_NAME = ?";
    private static final String GET_SECRET_KEY_SQL =
            "SELECT UM_ATTR_VALUE FROM UM_USER_ATTRIBUTE" +
                    "WHERE UM_USER_NAME = ? AND UM_TENANT_ID = ? AND UM_ATTR_NAME = ?";

    public static Tenant[] getAllTenants() throws MigrationClientException {

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

    public static String[] getAllDomainNames(AbstractUserStoreManager abstractUserStoreManager) {

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

    public static boolean isCustomUserStore(UserStoreManager userStoreManager) {

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

    public static String getSecretKeyClaimValue(String username,
                                                String claim, AbstractUserStoreManager abstractUserStoreManager)
            throws org.wso2.carbon.user.core.UserStoreException {

        return abstractUserStoreManager.getUserClaimValue(username, claim,
                DEFAULT_PROFILE);
    }


    public static void updateTotpSecretKeyInDb(String secretKey, String mappedAttribute, String username, int tenantId,
                                               Migrator migrator)
            throws MigrationClientException, SQLException {

        try (Connection connection = migrator.getDataSource(Schema.UM.getName()).getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_SECRET_KEY_SQL);
            preparedStatement.setString(1, secretKey);
            preparedStatement.setString(2, username);
            preparedStatement.setInt(3, tenantId);
            preparedStatement.setString(4, mappedAttribute);
            preparedStatement.executeUpdate();
        }
    }

    public static String getTotpSecretClaimFromDB(String username, int tenantId, String mappedAttr, Migrator migrator)
            throws MigrationClientException,
            SQLException {

        try (Connection connection = migrator.getDataSource(Schema.UM.getName()).getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(GET_SECRET_KEY_SQL);
            preparedStatement.setString(1, username);
            preparedStatement.setInt(2, tenantId);
            preparedStatement.setString(3, mappedAttr);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("UM_ATTR_VALUE");
                }
            }
        }
        return null;
    }

    public static List<TotpSecretData> getAllTotpSecretDataFromDb(int limit, int offset, Connection connection,
                                                                  String secretKeyMappedAtr,
                                                                  String verifiedSecretKeyMappedAttr)
            throws SQLException {

        List<TotpSecretData> totpSecretDataList = new ArrayList<>();
        boolean mysqlQueryUsed = false;
        String sql;
        if (connection.getMetaData().getDriverName().contains("MySQL")
                // We can't use the similar thing like above with DB2.Check
                // https://www.ibm.com/support/knowledgecenter/en/SSEPEK_10.0.0/java/src/tpc/imjcc_rjvjdapi.html#imjcc_rjvjdapi__d70364e1426
                || connection.getMetaData().getDatabaseProductName().contains("DB2")
                || connection.getMetaData().getDriverName().contains("H2")
                || connection.getMetaData().getDriverName().contains("PostgreSQL")) {
            sql = RETRIEVE_PAGINATED_TOTP_SECRET_KEY_CLAIM_DATA_FROM_JDBC_USERSTORE_WITH_MYSQL;
            mysqlQueryUsed = true;

        } else {
            sql = RETRIEVE_PAGINATED_TOTP_SECRET_KEY_CLAIM_DATA_FROM_JDBC_USERSTORE_WITH_OTHER;
        }
        try (PreparedStatement preparedStatement = connection
                .prepareStatement(sql)) {
            preparedStatement.setString(1, secretKeyMappedAtr);
            preparedStatement.setString(2, verifiedSecretKeyMappedAttr);
            // In mysql type queries, limit and offset values are changed.
            if (mysqlQueryUsed) {
                preparedStatement.setInt(3, limit);
                preparedStatement.setInt(4, offset);
            } else {
                preparedStatement.setInt(3, offset);
                preparedStatement.setInt(4, limit);
            }
            try (ResultSet resultSet = preparedStatement.executeQuery()) {

                while (resultSet.next()) {
                    totpSecretDataList.add(new TotpSecretData(resultSet.getString("UM_TENANT_ID"),
                            resultSet.getString("UM_USER_ID"),
                            resultSet.getString("UM_ATTR_VALUE"), resultSet.getString("UM_ATTR_NAME")));
                }
            }
        }
        return totpSecretDataList;
    }

    public static void updateNewTotpSecretsToDb(List<TotpSecretData> updatedTotpSecretDataList, Connection connection)
            throws SQLException, MigrationClientException {

        connection.setAutoCommit(false);
        try (PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_TOTP_SECRET_TO_JDBC_USERSTORE)) {
            for (TotpSecretData totpSecretData : updatedTotpSecretDataList) {
                preparedStatement.setString(1, totpSecretData.getEncryptedSeceretkeyValue());
                preparedStatement.setString(2, totpSecretData.getTenantId());
                preparedStatement.setString(3, totpSecretData.getUserName());
                preparedStatement.setString(4, totpSecretData.getDataKey());
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw new MigrationClientException(
                    "SQL error while retrieving datasource or database connection for identity claims table", e);
        }
    }

    public static void updateTotpSecretClaim(String username, String secretKey,
                                             AbstractUserStoreManager abstractUserStoreManager)
            throws org.wso2.carbon.user.core.UserStoreException {

        String value = abstractUserStoreManager.getUserClaimValue(username, TOTP_SECRET_KEY_CLAIM, DEFAULT_PROFILE);
        if (StringUtils.isNotEmpty(value)) {
            return;
        }

        Map<String, String> claimValues = new HashMap<String, String>() {{
            put(TOTP_SECRET_KEY_CLAIM, secretKey);
        }};
        abstractUserStoreManager.doSetUserClaimValues(username, claimValues, DEFAULT_PROFILE);
    }
}
