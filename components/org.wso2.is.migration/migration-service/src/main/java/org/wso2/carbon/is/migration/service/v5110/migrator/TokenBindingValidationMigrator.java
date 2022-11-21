/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.is.migration.service.v5110.migrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.identity.core.migrate.MigrationClientException;
import org.wso2.carbon.is.migration.service.Migrator;
import org.wso2.carbon.is.migration.service.v5110.bean.OIDCSPInfo;
import org.wso2.carbon.is.migration.service.v5110.dao.TokenBindingDAO;
import org.wso2.carbon.is.migration.util.Constant;
import org.wso2.carbon.is.migration.util.ReportUtil;
import org.wso2.carbon.is.migration.util.Schema;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import static org.wso2.carbon.is.migration.util.Constant.REPORT_PATH;

/**
 * This class handles the migration of the token binding validation property.
 */
public class TokenBindingValidationMigrator extends Migrator {

    private static final Logger log = LoggerFactory.getLogger(TokenBindingValidationMigrator.class);

    private ReportUtil reportUtil;

    TokenBindingDAO tokenBindingDAO = new TokenBindingDAO();

    @Override
    public void dryRun() throws MigrationClientException {

        log.info(Constant.MIGRATION_LOG + "Executing dry run for {}", this.getClass().getName());
        Properties migrationProperties = getMigratorConfig().getParameters();
        String reportPath = (String) migrationProperties.get(REPORT_PATH);
        try {
            reportUtil = new ReportUtil(reportPath);
            reportUtil.writeMessage("\n--- Summary of the report ---\n");
            reportUtil.writeMessage("Token Binding Validation parameter to be migrated for the following" +
                    " OIDC Service Providers..\n");
            reportUtil.writeMessage(
                    String.format("%20s | %20s ", "Tenant ID", "Consumer Key"));

            log.info(Constant.MIGRATION_LOG + "Started the dry run token binding validation migration.");
            List<OIDCSPInfo> oidcSpInfoList;
            try (Connection connection = getDataSource(Schema.IDENTITY.getName()).getConnection()) {
                try {
                    oidcSpInfoList = tokenBindingDAO.getOIDCServiceProvidersData(connection);
                    for (OIDCSPInfo oidcspInfo : oidcSpInfoList) {
                        if (!tokenBindingDAO.isTokenBindingValidationPropertyExists(connection,
                                oidcspInfo.getConsumerKey(), oidcspInfo.getTenantID())) {
                            reportUtil.writeMessage(
                                    String.format("%20s | %20s ", oidcspInfo.getTenantID(),
                                            oidcspInfo.getConsumerKey()));
                        }
                    }
                    reportUtil.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    String message = Constant.MIGRATION_LOG + "Error occurred while running the dry run.";
                    if (isContinueOnError()) {
                        log.error(message, e);
                    } else {
                        throw new MigrationClientException(message, e);
                    }
                }
            } catch (SQLException e) {
                String message = Constant.MIGRATION_LOG + "Error occurred while running the dry run.";
                if (isContinueOnError()) {
                    log.error(message, e);
                } else {
                    throw new MigrationClientException(message, e);
                }
            }
        } catch (IOException ex) {
            throw new MigrationClientException("Error while writing the dry run report.", ex);
        }
    }

    @Override
    public void migrate() throws MigrationClientException {

        List<OIDCSPInfo> oidcSpInfoList;
        try (Connection connection = getDataSource(Schema.IDENTITY.getName()).getConnection()) {
            try {
                oidcSpInfoList = tokenBindingDAO.getOIDCServiceProvidersData(connection);
                for (OIDCSPInfo oidcspInfo : oidcSpInfoList) {
                    if (!tokenBindingDAO.isTokenBindingValidationPropertyExists(connection,
                            oidcspInfo.getConsumerKey(), oidcspInfo.getTenantID())) {
                        tokenBindingDAO.addTokenBindingValidationParameter(connection,
                                oidcspInfo.getConsumerKey(), oidcspInfo.getTenantID());
                    }
                }
            } catch (SQLException e) {
                connection.rollback();
                String message = Constant.MIGRATION_LOG + "Error occurred while migrating the token binding " +
                        "validation property.";
                if (isContinueOnError()) {
                    log.error(message, e);
                } else {
                    throw new MigrationClientException(message, e);
                }
            }
        } catch (SQLException e) {
            String message = Constant.MIGRATION_LOG + "Error occurred while migrating the token binding validation " +
                    "property.";
            if (isContinueOnError()) {
                log.error(message, e);
            } else {
                throw new MigrationClientException(message, e);
            }
        }
    }
}
