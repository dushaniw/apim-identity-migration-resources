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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.identity.core.migrate.MigrationClientException;
import org.wso2.carbon.is.migration.service.Migrator;
import org.wso2.carbon.is.migration.service.v530.util.JDBCPersistenceUtil;
import org.wso2.carbon.is.migration.util.Schema;

import java.sql.*;
import java.util.HashMap;

/**
 * Migration implementation for migrating app inbound auth data.
 */
public class AppInboundAuthMigrator extends Migrator {

    private static final Logger log = LoggerFactory.getLogger(AppInboundAuthMigrator.class);
    private static final String PASSIVESTS = "passivests";
    private static final String OPENID = "openid";
    private static final String GET_APP_ID_COUNT =
            "SELECT APP_ID, COUNT(APP_ID) as count FROM SP_INBOUND_AUTH GROUP BY APP_ID HAVING COUNT(APP_ID) >= 2";
    private static final String ERROR_DELETING_AUTH_DATA =
            "An error occurred while deleting service provider inbound auth data.";
    private static final String ERROR_RETRIEVING_AUTH_DATA =
            "An error occurred while retrieving service provider inbound auth data.";
    private static final String GET_AUTH_DATA = "SELECT PROP_NAME FROM SP_INBOUND_AUTH " +
            "WHERE APP_ID = ? AND INBOUND_AUTH_TYPE = ?";
    private static final String DELETE_MULTIPLE_AUTH_ENTRIES =
            "DELETE FROM SP_INBOUND_AUTH WHERE APP_ID = ? AND (INBOUND_AUTH_TYPE = ? OR INBOUND_AUTH_TYPE = ?)";
    private static final String DELETE_AUTH_ENTRY = "DELETE from SP_INBOUND_AUTH WHERE APP_ID = ? AND " +
            "INBOUND_AUTH_TYPE = ?";

    @Override
    public void dryRun() throws MigrationClientException {

        log.info("Dry run capability not implemented in {} migrator.", this.getClass().getName());
    }

    @Override
    public void migrate() throws MigrationClientException {

        log.info("Started migrating app inbound authentication data.");
        HashMap<Integer, Integer> appids = getAppIDCountFromSPInboundAuthTable();
        deleteStaleInboundAuthEntries(appids);
        log.info("Inbound authentication data migration complete.");
    }

    /**
     * Function to retrieve app ID's along with their corresponding count.
     */
    private HashMap<Integer, Integer> getAppIDCountFromSPInboundAuthTable() throws MigrationClientException {

        log.info("Reading data from SP Auth table.");
        HashMap<Integer, Integer> appids = new HashMap<>();

        try (Connection connection = getDataSource(Schema.IDENTITY.getName()).getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(GET_APP_ID_COUNT)) {
            while (resultSet.next()) {
                Integer appid = resultSet.getInt("app_id");
                Integer count = resultSet.getInt("count");
                appids.put(appid, count);
            }
            return appids;
        } catch (SQLException e) {
            throw new MigrationClientException(ERROR_RETRIEVING_AUTH_DATA, e);
        }
    }

    /**
     * Function that contains the logic to delete stale inbound auth entries
     */
    private void deleteStaleInboundAuthEntries(HashMap<Integer, Integer> appids) throws MigrationClientException {

        if (appids.isEmpty()) {
            log.info("There are no stale entries.");
        } else {
            log.info("Removing stale inbound auth entries.");
            try (Connection connection = getDataSource(Schema.IDENTITY.getName()).getConnection()) {
                boolean autoCommitStatus = connection.getAutoCommit();
                connection.setAutoCommit(false);
                for (Integer code : appids.keySet()) {
                    Integer count = appids.get(code);
                    if (count == 3) {
                        try (PreparedStatement preparedStatement =
                                     connection.prepareStatement(DELETE_MULTIPLE_AUTH_ENTRIES)) {
                            preparedStatement.setInt(1, code);
                            preparedStatement.setString(2, PASSIVESTS);
                            preparedStatement.setString(3, OPENID);
                            preparedStatement.executeUpdate();
                        } catch (SQLException e) {
                            JDBCPersistenceUtil.rollbackTransaction(connection);
                            connection.setAutoCommit(autoCommitStatus);
                            throw new MigrationClientException(ERROR_DELETING_AUTH_DATA, e);
                        }
                    } else if (count == 2) {
                        try (PreparedStatement retrieveAuthData = connection.prepareStatement(GET_AUTH_DATA)) {
                            retrieveAuthData.setInt(1, code);
                            retrieveAuthData.setString(2, PASSIVESTS);
                            ResultSet resultSet = retrieveAuthData.executeQuery();
                            if (resultSet.next()) {
                                String propName = resultSet.getString("PROP_NAME");
                                try (PreparedStatement preparedStatement =
                                             connection.prepareStatement(DELETE_AUTH_ENTRY)) {
                                    if ("passiveSTSWReply".equalsIgnoreCase(propName)) {
                                        preparedStatement.setInt(1, code);
                                        preparedStatement.setString(2, OPENID);
                                        preparedStatement.executeUpdate();
                                    } else {
                                        preparedStatement.setInt(1, code);
                                        preparedStatement.setString(2, PASSIVESTS);
                                        preparedStatement.executeUpdate();
                                    }
                                } catch (SQLException e) {
                                    JDBCPersistenceUtil.rollbackTransaction(connection);
                                    connection.setAutoCommit(autoCommitStatus);
                                    throw new MigrationClientException(ERROR_DELETING_AUTH_DATA, e);
                                }
                            }
                        } catch (SQLException e) {
                            JDBCPersistenceUtil.rollbackTransaction(connection);
                            connection.setAutoCommit(autoCommitStatus);
                            throw new MigrationClientException(ERROR_RETRIEVING_AUTH_DATA, e);
                        }
                    }
                }
                JDBCPersistenceUtil.commitTransaction(connection);
                connection.setAutoCommit(autoCommitStatus);
            } catch(SQLException e){
                throw new MigrationClientException(ERROR_DELETING_AUTH_DATA, e);
            }
        }
    }
}
