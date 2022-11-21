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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.HashMap;
import java.util.TimeZone;

/**
 * Migration implementation for migrating recovery data.
 */
public class RecoveryDataMigrator extends Migrator {

    private static final Logger log = LoggerFactory.getLogger(RecoveryDataMigrator.class);
    private static final String UPDATE_TIMESTAMP_SQL =
            "UPDATE IDN_RECOVERY_DATA SET TIME_CREATED = ? WHERE CODE = ? AND TIME_CREATED = ?";
    private static final String GET_TIMESTAMPS = "SELECT CODE, TIME_CREATED FROM IDN_RECOVERY_DATA";
    private static final String UTC = "UTC";

    @Override
    public void dryRun() throws MigrationClientException {

        log.info("Dry run capability not implemented in {} migrator.", this.getClass().getName());
    }

    @Override
    public void migrate() throws MigrationClientException {

        log.info("Started migrating recovery data timestamps.");
        HashMap<String, Timestamp> timestamps = getTimestampsFromRecoveryTable();
        updateTimestampToUTC(timestamps);
        log.info("Recovery data migration complete.");
    }

    private HashMap<String, Timestamp> getTimestampsFromRecoveryTable() throws MigrationClientException {

        log.info("Reading data from Recovery Data table.");
        HashMap<String, Timestamp> timestamps = new HashMap<>();

        try (Connection connection = getDataSource(Schema.IDENTITY.getName()).getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(GET_TIMESTAMPS)) {
            while (resultSet.next()) {
                String code = resultSet.getString("code");
                Timestamp timestamp = resultSet.getTimestamp("time_created");
                timestamps.put(code, timestamp);
            }
            return timestamps;
        } catch (SQLException e) {
            throw new MigrationClientException("An error occurred while reading recovery data.", e);
        }
    }

    private void updateTimestampToUTC(HashMap<String, Timestamp> timestamps) throws MigrationClientException {

        if (!timestamps.isEmpty()) {
            log.info("Converting timestamps to UTC timestamps in the Recovery Data table.");
        }
        try (Connection connection = getDataSource(Schema.IDENTITY.getName()).getConnection()) {
            boolean autoCommitStatus = connection.getAutoCommit();
            connection.setAutoCommit(false);
            for (String code : timestamps.keySet()) {
                Timestamp localTimestamp = timestamps.get(code);
                try (PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_TIMESTAMP_SQL)) {
                    preparedStatement.setTimestamp(1, localTimestamp,
                            Calendar.getInstance(TimeZone.getTimeZone(UTC)));
                    preparedStatement.setString(2, code);
                    preparedStatement.setTimestamp(3, localTimestamp);
                    preparedStatement.executeUpdate();
                } catch (SQLException e) {
                    JDBCPersistenceUtil.rollbackTransaction(connection);
                    connection.setAutoCommit(autoCommitStatus);
                    throw new MigrationClientException("An error occurred while updating recovery data.", e);
                }
            }
            JDBCPersistenceUtil.commitTransaction(connection);
            connection.setAutoCommit(autoCommitStatus);
        } catch (SQLException e) {
            throw new MigrationClientException("An error occurred while updating recovery data.", e);
        }
    }
}
