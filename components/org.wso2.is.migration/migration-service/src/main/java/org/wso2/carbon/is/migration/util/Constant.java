/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

/**
 * Holds common constants in migration service.
 */
public class Constant {

    public static final String SCHEMA_MIGRATOR_NAME = "SchemaMigrator";
    public static final String CLAIM_DATA_MIGRATOR_NAME = "ClaimDataMigrator";
    public static final String EMAIL_TEMPLATE_DATA_MIGRATOR_NAME = "EmailTemplateDataMigrator";
    public static final String MIGRATION_RESOURCE_HOME = "migration-resources";
    public static final String MIGRATION_RESOURCE_DBSCRIPTS = "dbscripts";
    public static final String MIGRATION_RESOURCE_DATA_FILES = "data";
    public static final String DRY_RUN = "dryRun";

    public static final String MIGRATION_CONFIG_FILE_NAME = "migration-config.yaml";

    public static final String CARBON_HOME = "carbon.home";
    public static final int SUPER_TENANT_ID = -1234;
    public static final String MIGRATION_LOG = " WSO2 Product Migration Service Task : ";

    public static final String IDENTITY_DB_SCRIPT = "identity";
    public static final String UM_DB_SCRIPT = "um";

    public static final String EVENT_PUBLISHER_PATH = "/repository/deployment/server/eventpublishers";

    public static final String CLAIM_CONFIG = "claim-config.xml";

    public static final String LOCATION = "location";
    public static final String MYSQL_5_7 = "mysql5.7";
    public static final String MYSQL_CLUSTER = "mysql_cluster";
    public static final String MYSQL_CLUSTER_5_7 = "mysql-cluster-5.7";
    public static final String DELIMITER = "DELIMITER";

    public static final String EMAIL_TEMPLATE_PATH = "/identity/email";
    public static final String SKIP_SUPER_TENANT_EMAIL_TEMPLATE_MIGRATION = "skipSuperTenantEmailTemplateMigration";
    public static final String TEMPLATE_TYPE = "type";
    public static final String TEMPLATE_TYPE_DISPLAY_NAME = "display";
    public static final String TEMPLATE_LOCALE = "locale";
    public static final String TEMPLATE_CONTENT_TYPE = "emailContentType";

    public static final String TEMPLATE_SUBJECT = "subject";
    public static final String TEMPLATE_BODY = "body";
    public static final String TEMPLATE_FOOTER = "footer";

    public static final String EMAIL_TEMPLATE_NAME = "templateName";
    public static final String EMAIL_TEMPLATE_TYPE_DISPLAY_NAME = "templateDisplayName";

    public static final String INCREMENT_PARAMETER_NAME = "increment";
    public static final String STARTING_POINT_PARAMETER_NAME = "startingPoint";
    public static final String MIGRATING_DOMAINS = "migratingDomains";
    public static final String FORCE_UPDATE_USER_ID = "forceUpdateUserId";
    public static final String TENANT_DOMAIN = "tenantDomain";
    public static final String SCIM_ENABLED = "scimEnabled";
    public static final String MIGRATE_ALL = "migrateAll";
    public static final String REPORT_PATH = "reportPath";

    public static final String USER_ID_CLAIM = "http://wso2.org/claims/userid";
    public static final String USERNAME_CLAIM = "http://wso2.org/claims/username";
    public static final String TOTP_SECRET_KEY_CLAIM = "http://wso2.org/claims/identity/secretkey";
    public static final String TOTP_VERIFIED_SECRET_KEY_CLAIM = "http://wso2.org/claims/identity/verifySecretkey";

    public static final String WORKFLOW_REQUEST_COLUMN = "REQUEST";

    public static final String SERVER_INTERNAL_CRYPTO_PROVIDER = "CryptoService.InternalCryptoProviderClassName";
    public static final String SERVER_SYMMETRIC_KEY = "CryptoService.Secret";
    public static final String SYMMETRIC_KEY_CRYPTO_PROVIDER = "org.wso2.carbon.crypto.provider" +
            ".SymmetricKeyInternalCryptoProvider";

    public static class ClaimDataMigratorConstants {

        public static final String MIGRATOR_PARAMETER_USE_OWN_CLAIM_DATA_FILE = "useOwnClaimDataFile";
        public static final String MIGRATOR_PARAMETER_OVERRIDE_EXISTING_CLAIMS = "overrideExistingClaims";
    }
}
