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
package org.wso2.carbon.is.migration.service.v5110.migrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.identity.core.migrate.MigrationClientException;
import org.wso2.carbon.is.migration.service.SchemaMigrator;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Executes Configuration Management DB scripts if they do not exist.
 */
public class ConfigurationManagementSchemaMigrator extends SchemaMigrator {

    private static Logger log = LoggerFactory.getLogger(ConfigurationManagementSchemaMigrator.class);
    private static final String IDN_CONFIG_DB_CHECK_SQL = "SELECT 1 FROM IDN_CONFIG_TYPE";

    @Override
    public void dryRun() throws MigrationClientException {

        log.info("Dry run capability not implemented in {} migrator.", this.getClass().getName());
    }

    @Override
    public void migrate() throws MigrationClientException {

        if (!isConfigurationManagementDatabaseStructureCreated()) {
            log.info("Configuration Management tables does not exist in the database. Hence creating.");
            super.migrate();
        } else {
            log.info("Configuration Management tables already exist in the database. Hence skipping.");
        }
    }

    /**
     * Checks whether Configuration Management database tables are created.
     *
     * @return <code>true</code> if Configuration Management database tables exist, else <code>false</code>.
     */
    private boolean isConfigurationManagementDatabaseStructureCreated() throws MigrationClientException {

        try (Connection con = getDataSource().getConnection()) {
            con.setAutoCommit(false);
            log.info("Running a query to test the database configuration management tables existence.");
            try (Statement stm = con.createStatement()) {
                stm.executeQuery(IDN_CONFIG_DB_CHECK_SQL);
                con.commit();
            } catch (SQLException ex) {
                try {
                    con.rollback();
                } catch (SQLException e) {
                    log.error("An error occurred while rolling back transactions.", e);
                }
                return false;
            }
        } catch (SQLException e) {
            log.error("An error occurred while retrieving the connection.", e);
        }
        return true;
    }
}
