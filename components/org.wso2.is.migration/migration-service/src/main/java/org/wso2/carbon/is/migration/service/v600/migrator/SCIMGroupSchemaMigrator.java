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
import org.wso2.carbon.is.migration.service.SchemaMigrator;
import org.wso2.carbon.is.migration.util.Constant;
import org.wso2.carbon.is.migration.util.ReportUtil;
import org.wso2.carbon.is.migration.util.Schema;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import static org.wso2.carbon.is.migration.util.Constant.REPORT_PATH;

public class SCIMGroupSchemaMigrator extends SchemaMigrator {

    private static final Logger log = LoggerFactory.getLogger(SCIMGroupSchemaMigrator.class);
    private static final String GET_IDN_SCIM_GROUP_DUPLICATE_ENTRIES =
            "SELECT TENANT_ID, ROLE_NAME, ATTR_NAME, COUNT(*) AS COUNT FROM IDN_SCIM_GROUP " +
                    "GROUP BY TENANT_ID, ROLE_NAME, ATTR_NAME HAVING COUNT(*) > 1";
    private static final String TENANT_ID = "tenant_id";
    private static final String ROLE_NAME = "role_name";
    private static final String ATTR_NAME = "attr_name";

    @Override
    public void migrate() throws MigrationClientException {

        super.migrate();
    }

    @Override
    public void dryRun() throws MigrationClientException {

        log.info(Constant.MIGRATION_LOG + "Executing dry run for {}", this.getClass().getName());
        Properties migrationProperties = getMigratorConfig().getParameters();
        String reportPath = (String) migrationProperties.get(REPORT_PATH);

        try {
            ReportUtil reportUtil = new ReportUtil(reportPath);
            reportUtil.writeMessage("\n--- Summary of the report ---\n");
            log.info(Constant.MIGRATION_LOG + "Started the dry run for IDN_SCIM_GROUP schema migration.");

            try (Connection connection = getDataSource(Schema.IDENTITY.getName()).getConnection();
                 Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
                         ResultSet.CONCUR_READ_ONLY);
                 ResultSet resultSet = statement.executeQuery(GET_IDN_SCIM_GROUP_DUPLICATE_ENTRIES)) {
                if (!resultSet.isBeforeFirst()) {
                    reportUtil.writeMessage("No issues detected with Schema Migration for the IDN_SCIM_GROUP table.");
                    log.info(Constant.MIGRATION_LOG + "No issues detected with Schema Migration for the " +
                            "IDN_SCIM_GROUP table.");
                } else {
                    reportUtil.writeMessage(
                            String.format("%10s | %20s | %80s | %20s", TENANT_ID, ROLE_NAME,
                                    ATTR_NAME, "NUMBER OF ENTRIES"));
                    while (resultSet.next()) {
                        reportUtil.writeMessage(
                                String.format("%10d | %20s | %80s | %20d", resultSet.getInt(TENANT_ID),
                                        resultSet.getString(ROLE_NAME), resultSet.getString(ATTR_NAME),
                                        resultSet.getInt("count")));
                    }
                    reportUtil.writeMessage("\nThe existence of the above duplicate entries in the IDN_SCIM_GROUP " +
                            "table in the database prevents the migration client from successfully completing " +
                            "the migration tasks. Manually remove the duplicate entries before proceeding with " +
                            "the migration.");
                    String message = "There are entries with duplicates in the IDN_SCIM_GROUP table which would " +
                            "violate a unique key between the fields (tenant_id, role_name, attr_name). Refer the " +
                            "report at " + reportPath + " to get more details about the duplicates and delete the " +
                            "duplicates before proceeding with the migration.";
                    log.error(Constant.MIGRATION_LOG + message);
                }
            } catch (SQLException e) {
                reportUtil.writeMessage("An error occurred while generating the report.");
                throw new MigrationClientException("An error occurred while reading IDN_SCIM_GROUP data.", e);
            }
            reportUtil.commit();
        } catch (IOException e) {
            throw new MigrationClientException("Error while writing the dry run report.", e);
        }
    }
}
