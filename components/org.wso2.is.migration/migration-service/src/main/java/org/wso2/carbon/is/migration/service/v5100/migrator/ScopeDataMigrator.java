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
import org.wso2.carbon.is.migration.service.Migrator;
import org.wso2.carbon.is.migration.util.Schema;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ScopeDataMigrator extends Migrator {

    private static final Logger log = LoggerFactory.getLogger(ScopeDataMigrator.class);

    private static final String ADD_SCOPE_TYPE_COLUMN_MYSQL_MSSQL = "ALTER TABLE IDN_OAUTH2_SCOPE ADD " +
            "SCOPE_TYPE VARCHAR(255) NOT NULL DEFAULT 'OAUTH2'";

    private static final String ADD_SCOPE_TYPE_COLUMN_H2_DB2_POSTGRESQL = "ALTER TABLE IDN_OAUTH2_SCOPE ADD COLUMN " +
            "SCOPE_TYPE VARCHAR(255) NOT NULL DEFAULT 'OAUTH2';";

    private static final String ADD_SCOPE_TYPE_COLUMN_INFORMIX = "ALTER TABLE IDN_OAUTH2_SCOPE ADD COLUMN " +
            "SCOPE_TYPE VARCHAR(255) NOT NULL DEFAULT 'OAUTH2'";

    private static final String ADD_SCOPE_TYPE_COLUMN_ORACLE = "ALTER TABLE IDN_OAUTH2_SCOPE ADD SCOPE_TYPE " +
            "VARCHAR(255) DEFAULT 'OAUTH2' NOT NULL";

    public static final String RETRIEVE_IDN_OAUTH2_SCOPE_TABLE_MYSQL = "SELECT SCOPE_TYPE " +
            "FROM IDN_OAUTH2_SCOPE LIMIT 1";
    public static final String RETRIEVE_IDN_OAUTH2_SCOPE_TABLE_DB2SQL = "SELECT SCOPE_TYPE " +
            "FROM IDN_OAUTH2_SCOPE FETCH FIRST 1 ROWS ONLY";
    public static final String RETRIEVE_IDN_OAUTH2_SCOPE_TABLE_MSSQL = "SELECT TOP 1 SCOPE_TYPE " +
            "FROM IDN_OAUTH2_SCOPE";
    public static final String RETRIEVE_IDN_OAUTH2_SCOPE_TABLE_INFORMIX = "SELECT FIRST 1 SCOPE_TYPE " +
            "FROM IDN_OAUTH2_SCOPE";
    public static final String RETRIEVE_IDN_OAUTH2_SCOPE_TABLE_ORACLE = "SELECT SCOPE_TYPE " +
            "FROM IDN_OAUTH2_SCOPE WHERE ROWNUM < 2";

    private static final String SCOPE_TYPE_COLUMN = "SCOPE_TYPE";

    @Override
    public void dryRun() throws MigrationClientException {

        log.info("Dry run capability not implemented in {} migrator.", this.getClass().getName());
    }

    @Override
    public void migrate() throws MigrationClientException {

        boolean isScopeTypeColumnExists;
        try (Connection connection = getDataSource(Schema.IDENTITY.getName()).getConnection()) {
            connection.setAutoCommit(false);
            isScopeTypeColumnExists = isScopeTypeColumnExists(connection);
            connection.rollback();
        } catch (SQLException ex) {
            throw new MigrationClientException("Error occurred while creating the SCOPE_TYPE column.", ex);
        }

        if (!isScopeTypeColumnExists) {
            try (Connection connection = getDataSource(Schema.IDENTITY.getName()).getConnection()) {
                try {
                    connection.setAutoCommit(false);
                    createScopeTypeColumn(connection);
                    connection.commit();
                } catch (SQLException ex) {
                    connection.rollback();
                    throw ex;
                }
            } catch (SQLException ex) {
                throw new MigrationClientException("Error occurred while creating the SCOPE_TYPE column.", ex);
            }
        }
    }

    private void createScopeTypeColumn(Connection connection) throws SQLException {

        String sql;
        if (connection.getMetaData().getDriverName().contains("MySQL") ||
                connection.getMetaData().getDriverName().contains("MS SQL") || connection.getMetaData()
                .getDriverName().contains("Microsoft")) {
            sql = ADD_SCOPE_TYPE_COLUMN_MYSQL_MSSQL;
        } else if (connection.getMetaData().getDriverName().contains("H2") ||
                connection.getMetaData().getDatabaseProductName().contains("DB2") ||
                connection.getMetaData().getDriverName().contains("PostgreSQL")) {
            sql = ADD_SCOPE_TYPE_COLUMN_H2_DB2_POSTGRESQL;
        } else if (connection.getMetaData().getDriverName().contains("Informix")) {
            // Driver name = "IBM Informix JDBC Driver for IBM Informix Dynamic Server"
            sql = ADD_SCOPE_TYPE_COLUMN_H2_DB2_POSTGRESQL;
        } else {
            sql = ADD_SCOPE_TYPE_COLUMN_ORACLE;
        }
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.executeUpdate();
        }
    }

    private boolean isScopeTypeColumnExists(Connection connection) throws SQLException {

        String sql;
        boolean isScopeTypeColumnExists;
        if (connection.getMetaData().getDriverName().contains("MySQL") || connection.getMetaData().getDriverName()
                .contains("H2")) {
            sql = RETRIEVE_IDN_OAUTH2_SCOPE_TABLE_MYSQL;
        } else if (connection.getMetaData().getDatabaseProductName().contains("DB2")) {
            sql = RETRIEVE_IDN_OAUTH2_SCOPE_TABLE_DB2SQL;
        } else if (connection.getMetaData().getDriverName().contains("MS SQL") || connection.getMetaData()
                .getDriverName().contains("Microsoft")) {
            sql = RETRIEVE_IDN_OAUTH2_SCOPE_TABLE_MSSQL;
        } else if (connection.getMetaData().getDriverName().contains("PostgreSQL")) {
            sql = RETRIEVE_IDN_OAUTH2_SCOPE_TABLE_MYSQL;
        } else if (connection.getMetaData().getDriverName().contains("Informix")) {
            // Driver name = "IBM Informix JDBC Driver for IBM Informix Dynamic Server"
            sql = RETRIEVE_IDN_OAUTH2_SCOPE_TABLE_INFORMIX;
        } else {
            sql = RETRIEVE_IDN_OAUTH2_SCOPE_TABLE_ORACLE;
        }

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            try {
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet != null) {
                    resultSet.findColumn(SCOPE_TYPE_COLUMN);
                    isScopeTypeColumnExists = true;
                } else {
                    isScopeTypeColumnExists = false;
                }
            } catch (SQLException e) {
                isScopeTypeColumnExists = false;
            }
        } catch (SQLException e) {
            isScopeTypeColumnExists = false;
        }
        return isScopeTypeColumnExists;
    }
}
