/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.com).
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.is.migration.service.v600.migrator;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.identity.core.migrate.MigrationClientException;
import org.wso2.carbon.is.migration.service.Migrator;
import org.wso2.carbon.is.migration.service.v530.util.JDBCPersistenceUtil;
import org.wso2.carbon.is.migration.util.Schema;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;

import static org.wso2.carbon.base.MultitenantConstants.SUPER_TENANT_ID;

/**
 * Migration implementation for updating the redirect URLs and access token binding type of System Applications.
 */
public class SystemAppMigrator extends Migrator {

    private static final Logger log = LoggerFactory.getLogger(SystemAppMigrator.class);

    private static final String UPDATE_REDIRECT_URL_SQL =
            "UPDATE IDN_OAUTH_CONSUMER_APPS SET CALLBACK_URL = ? WHERE CONSUMER_KEY = ?";
    private static final String UPDATE_ACCESS_URL_SQL =
            "UPDATE SP_APP SET ACCESS_URL = ? WHERE TENANT_ID = ? AND APP_NAME = ?";
    private static final String GET_SP_METADATA = "SELECT SP.ID, SM.NAME FROM SP_APP SP JOIN SP_METADATA SM " +
            "ON SP.ID = SM.SP_ID WHERE SP.TENANT_ID = ? AND SP.APP_NAME = ?";
    private static final String ADD_SP_METADATA = "INSERT INTO SP_METADATA (SP_ID, NAME, VALUE, DISPLAY_NAME, " +
            "TENANT_ID) VALUES (?,?,?,?,?)";
    private static final String UPDATE_ACCESS_TOKEN_BINDING_TYPE =
            "UPDATE IDN_OIDC_PROPERTY SET PROPERTY_VALUE = ? WHERE PROPERTY_KEY = ? AND CONSUMER_KEY = ?";
    private static final String GET_SP_CLAIMS = "SELECT SP.ID, SP_CLAIM FROM SP_APP SP JOIN SP_CLAIM_MAPPING SCM " +
            "ON SP.ID = SCM.APP_ID WHERE SP.TENANT_ID = ? AND SP.APP_NAME = ?";
    private static final String ADD_SP_CLAIM = "INSERT INTO SP_CLAIM_MAPPING (TENANT_ID, IDP_CLAIM, SP_CLAIM, APP_ID, " +
            "IS_REQUESTED, IS_MANDATORY) VALUES (?,?,?,?,?,?)";

    private static final String CONSOLE_REDIRECT_URL = "consoleRedirectUrl";
    private static final String CONSOLE_ACCESS_URL = "consoleAccessUrl";
    private static final String MYACCOUNT_REDIRECT_URL = "myaccountRedirectUrl";
    private static final String MYACCOUNT_ACCESS_URL = "myaccountAccessUrl";
    private static final String ACCESS_TOKEN_BINDING_TYPE = "accessTokenBindingType";

    private static final String USERNAME_CLAIM_URI = "http://wso2.org/claims/username";
    private static final String CONSOLE_APP_NAME = "Console";
    private static final String MYACCOUNT_APP_NAME = "My Account";
    private static final String IS_MANAGEMENT_APP_METADATA = "isManagementApp";
    private static final String USE_USER_ID_FOR_DEFAULT_SUBJECT_METADATA = "useUserIdForDefaultSubject";

    @Override
    public void dryRun() throws MigrationClientException {

        log.info("Dry run capability not implemented in {} migrator.", this.getClass().getName());
    }

    @Override
    public void migrate() throws MigrationClientException {

        if (getMigratorConfig() == null || getMigratorConfig().getParameters() == null) {
            log.info("SystemAppMigrator parameters not provided. ");
            return;
        }

        try (Connection connection = getDataSource(Schema.IDENTITY.getName()).getConnection()) {
            boolean autoCommitStatus = connection.getAutoCommit();
            connection.setAutoCommit(false);

            log.info("Started updating System application.");
            updateRedirectUrls(connection, autoCommitStatus);
            updateAccessUrls(connection, autoCommitStatus);
            updateAccessTokenBindingType(connection, autoCommitStatus);
            updateSPClaim(CONSOLE_APP_NAME, USERNAME_CLAIM_URI, connection, autoCommitStatus);
            updateSPClaim(MYACCOUNT_APP_NAME, USERNAME_CLAIM_URI, connection, autoCommitStatus);
            updateSystemAppMetadata(CONSOLE_APP_NAME, connection, autoCommitStatus);
            updateSystemAppMetadata(MYACCOUNT_APP_NAME, connection, autoCommitStatus);

            log.info("System application migration complete.");

            JDBCPersistenceUtil.commitTransaction(connection);
            connection.setAutoCommit(autoCommitStatus);
        } catch (SQLException e) {
            throw new MigrationClientException("An error occurred while updating system application", e);
        }
    }

    private void updateRedirectUrls(Connection connection, boolean autoCommitStatus) throws MigrationClientException {

        log.info("Started updating System application redirect URLs.");
        String consoleRedirectUrl = getMigratorConfig().getParameters().getProperty(CONSOLE_REDIRECT_URL);
        String myaccountRedirectUrl = getMigratorConfig().getParameters().getProperty(MYACCOUNT_REDIRECT_URL);
        try {
            try (PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_REDIRECT_URL_SQL)) {
                if (StringUtils.isNotBlank(consoleRedirectUrl)) {
                    preparedStatement.setString(1, consoleRedirectUrl);
                    preparedStatement.setString(2, "CONSOLE");
                    preparedStatement.executeUpdate();
                }
                if (StringUtils.isNotBlank(myaccountRedirectUrl)) {
                    preparedStatement.setString(1, myaccountRedirectUrl);
                    preparedStatement.setString(2, "MY_ACCOUNT");
                    preparedStatement.executeUpdate();
                }
            } catch (SQLException e) {
                JDBCPersistenceUtil.rollbackTransaction(connection);
                connection.setAutoCommit(autoCommitStatus);
                throw new MigrationClientException("An error occurred while updating System application" +
                        " redirect URLs.", e);
            }
        } catch (SQLException e) {
            throw new MigrationClientException("An error occurred while updating System application" +
                    " redirect URLs.", e);
        }
        log.info("System application redirect url migration complete.");
    }

    private void updateAccessUrls(Connection connection, boolean autoCommitStatus) throws MigrationClientException {

        log.info("Started updating System application access URLs.");
        String consoleAccessUrl = getMigratorConfig().getParameters().getProperty(CONSOLE_ACCESS_URL);
        String myaccountAccessUrl = getMigratorConfig().getParameters().getProperty(MYACCOUNT_ACCESS_URL);
        try {
            try (PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_ACCESS_URL_SQL)) {
                if (StringUtils.isNotBlank(consoleAccessUrl)) {
                    preparedStatement.setString(1, consoleAccessUrl);
                    preparedStatement.setInt(2, SUPER_TENANT_ID);
                    preparedStatement.setString(3, CONSOLE_APP_NAME);
                    preparedStatement.executeUpdate();
                }
                if (StringUtils.isNotBlank(myaccountAccessUrl)) {
                    preparedStatement.setString(1, myaccountAccessUrl);
                    preparedStatement.setInt(2, SUPER_TENANT_ID);
                    preparedStatement.setString(3, MYACCOUNT_APP_NAME);
                    preparedStatement.executeUpdate();
                }
            } catch (SQLException e) {
                JDBCPersistenceUtil.rollbackTransaction(connection);
                connection.setAutoCommit(autoCommitStatus);
                throw new MigrationClientException("An error occurred while updating System application" +
                        " access URLs.", e);
            }
        } catch (SQLException e) {
            throw new MigrationClientException("An error occurred while updating System application" +
                    " access URLs.", e);
        }
        log.info("System application access url migration complete.");
    }

    private void updateAccessTokenBindingType(Connection connection, boolean autoCommitStatus)
            throws MigrationClientException {

        log.info("Started migrating system application access token binding type.");
        String accessTokenBindingType = getMigratorConfig().getParameters().getProperty(ACCESS_TOKEN_BINDING_TYPE);
        try {
            try (PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_ACCESS_TOKEN_BINDING_TYPE)) {
                if (StringUtils.isNotBlank(accessTokenBindingType)) {
                    preparedStatement.setString(1, accessTokenBindingType);
                    preparedStatement.setString(2, "tokenBindingType");

                    preparedStatement.setString(3, "CONSOLE");
                    preparedStatement.executeUpdate();

                    preparedStatement.setString(3, "MY_ACCOUNT");
                    preparedStatement.executeUpdate();
                }

            } catch (SQLException e) {
                JDBCPersistenceUtil.rollbackTransaction(connection);
                connection.setAutoCommit(autoCommitStatus);
                throw new MigrationClientException("An error occurred while updating system application" +
                        " access token binding type.", e);
            }
        } catch (SQLException e) {
            throw new MigrationClientException("An error occurred while updating system application" +
                    " access token binding type", e);
        }
        log.info("System application access token binding type migration complete.");
    }

    private void updateSPClaim(String appName, String claim, Connection connection, boolean autoCommitStatus)
            throws MigrationClientException {

        int appId = -1;
        ArrayList<String> spClaims = new ArrayList<>();

        try {
            try (PreparedStatement preparedStatement = connection.prepareStatement(GET_SP_CLAIMS)) {
                preparedStatement.setInt(1, SUPER_TENANT_ID);
                preparedStatement.setString(2, appName);

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        appId = resultSet.getInt("ID");
                        spClaims.add(resultSet.getString("SP_CLAIM"));
                    }
                }
                if (appId > 0 && !spClaims.contains(claim)) {
                    addUsernameClaimMapping(appId, claim, connection, autoCommitStatus);
                } else if (appId == -1) {
                    log.info(String.format("Application %s does not exist.", appName));
                }
            } catch (SQLException e) {
                JDBCPersistenceUtil.rollbackTransaction(connection);
                connection.setAutoCommit(autoCommitStatus);
                throw new MigrationClientException("An error occurred while retrieving claims for application "
                        + appName, e);
            }
        } catch (SQLException e) {
            throw new MigrationClientException("An error occurred while retrieving claims for application "
                    + appName, e);
        }
    }

    private void addUsernameClaimMapping(int appId, String claim, Connection connection, boolean autoCommitStatus)
            throws MigrationClientException {

        try {
            try (PreparedStatement preparedStatement = connection.prepareStatement(ADD_SP_CLAIM)) {
                    preparedStatement.setInt(1, SUPER_TENANT_ID);
                    preparedStatement.setString(2, claim);
                    preparedStatement.setString(3, claim);
                    preparedStatement.setInt(4, appId);
                    preparedStatement.setString(5, "1");
                    preparedStatement.setString(6, "0");
                    preparedStatement.executeUpdate();

            } catch (SQLException e) {
                JDBCPersistenceUtil.rollbackTransaction(connection);
                connection.setAutoCommit(autoCommitStatus);
                throw new MigrationClientException("An error occurred while updating claims of application.", e);
            }
        } catch (SQLException e) {
            throw new MigrationClientException("An error occurred while updating claims of application.", e);
        }
    }

    private void updateSystemAppMetadata(String appName,Connection connection, boolean autoCommitStatus)
            throws MigrationClientException {

        int appId = -1;
        ArrayList<String> spMetadataEntries = new ArrayList<>();

        try {
            try (PreparedStatement preparedStatement = connection.prepareStatement(GET_SP_METADATA)) {
                preparedStatement.setInt(1, SUPER_TENANT_ID);
                preparedStatement.setString(2, appName);

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        appId = resultSet.getInt("ID");
                        spMetadataEntries.add(resultSet.getString("NAME"));
                    }
                }
                if (appId > 0) {
                    if (!spMetadataEntries.contains(USE_USER_ID_FOR_DEFAULT_SUBJECT_METADATA)) {
                        addSpMetadata(appId, USE_USER_ID_FOR_DEFAULT_SUBJECT_METADATA, null,
                                connection, autoCommitStatus);
                    }
                    if (!spMetadataEntries.contains(IS_MANAGEMENT_APP_METADATA)) {
                        addSpMetadata(appId, IS_MANAGEMENT_APP_METADATA, "Is Management Application",
                                connection, autoCommitStatus);
                    }
                } else {
                    log.info(String.format("Application %s does not exist.", appName));
                }
            } catch (SQLException e) {
                JDBCPersistenceUtil.rollbackTransaction(connection);
                connection.setAutoCommit(autoCommitStatus);
                throw new MigrationClientException("An error occurred while retrieving metadata for application "
                        + appName, e);
            }
        } catch (SQLException e) {
            throw new MigrationClientException("An error occurred while retrieving metadata for application "
                    + appName, e);
        }
    }

    private void addSpMetadata(int appId, String metadataName, String displayName, Connection connection,
                               boolean autoCommitStatus) throws MigrationClientException {

        try {
            try (PreparedStatement preparedStatement = connection.prepareStatement(ADD_SP_METADATA)) {
                preparedStatement.setInt(1, appId);
                preparedStatement.setString(2, metadataName);
                preparedStatement.setString(3, Boolean.TRUE.toString());
                preparedStatement.setObject(4, displayName, Types.VARCHAR);
                preparedStatement.setInt(5, SUPER_TENANT_ID);
                preparedStatement.executeUpdate();

            } catch (SQLException e) {
                JDBCPersistenceUtil.rollbackTransaction(connection);
                connection.setAutoCommit(autoCommitStatus);
                throw new MigrationClientException("An error occurred while updating metadata of application.", e);
            }
        } catch (SQLException e) {
            throw new MigrationClientException("An error occurred while updating metadata of application.", e);
        }
    }
}
