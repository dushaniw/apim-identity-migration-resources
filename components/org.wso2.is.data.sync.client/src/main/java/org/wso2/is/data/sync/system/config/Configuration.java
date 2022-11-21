/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.is.data.sync.system.config;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.is.data.sync.system.database.SchemaInfo;
import org.wso2.is.data.sync.system.database.SchemaTableMapping;
import org.wso2.is.data.sync.system.exception.SyncClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.wso2.is.data.sync.system.util.Constant.DEFAULT_BATCH_SIZE;
import static org.wso2.is.data.sync.system.util.Constant.DEFAULT_SYNC_INTERVAL;
import static org.wso2.is.data.sync.system.util.Constant.JVM_PROPERTY_BATCH_SIZE;
import static org.wso2.is.data.sync.system.util.Constant.JVM_PROPERTY_CONSENT_SCHEMA;
import static org.wso2.is.data.sync.system.util.Constant.JVM_PROPERTY_IDENTITY_SCHEMA;
import static org.wso2.is.data.sync.system.util.Constant.JVM_PROPERTY_REG_SCHEMA;
import static org.wso2.is.data.sync.system.util.Constant.JVM_PROPERTY_SOURCE_VERSION;
import static org.wso2.is.data.sync.system.util.Constant.JVM_PROPERTY_SYNC_INTERVAL;
import static org.wso2.is.data.sync.system.util.Constant.JVM_PROPERTY_SYNC_TABLES;
import static org.wso2.is.data.sync.system.util.Constant.JVM_PROPERTY_TARGET_VERSION;
import static org.wso2.is.data.sync.system.util.Constant.JVM_PROPERTY_UM_SCHEMA;
import static org.wso2.is.data.sync.system.util.Constant.SCHEMA_TYPE_CONSENT;
import static org.wso2.is.data.sync.system.util.Constant.SCHEMA_TYPE_IDENTITY;
import static org.wso2.is.data.sync.system.util.Constant.SCHEMA_TYPE_REGISTRY;
import static org.wso2.is.data.sync.system.util.Constant.SCHEMA_TYPE_UM;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;


/**
 * This class represents the configuration data required for data sync client. Instance of this should be build from
 * {@link ConfigurationBuilder}.
 */
public class Configuration {

    private String sourceVersion;
    private String targetVersion;
    private int batchSize = DEFAULT_BATCH_SIZE;
    private long syncInterval = DEFAULT_SYNC_INTERVAL;
    private List<String> syncTables = new ArrayList<>();
    private List<SchemaInfo> schemaInfoList = new ArrayList<>();

    private String oldEncryptionAlgorithm;

    private Configuration() {

    }

    public String getSourceVersion() {

        return sourceVersion;
    }

    private void setSourceVersion(String sourceVersion) {

        this.sourceVersion = sourceVersion;
    }

    public String getTargetVersion() {

        return targetVersion;
    }

    private void setTargetVersion(String targetVersion) {

        this.targetVersion = targetVersion;
    }

    public int getBatchSize() {

        return batchSize;
    }

    private void setBatchSize(int batchSize) {

        this.batchSize = batchSize;
    }

    public long getSyncInterval() {

        return syncInterval;
    }

    private void setSyncInterval(long syncInterval) {

        this.syncInterval = syncInterval;
    }

    public List<String> getSyncTables() {

        return syncTables;
    }

    private void setSyncTables(List<String> syncTables) {

        this.syncTables = syncTables;
    }

    public List<SchemaInfo> getSchemaInfoList() {

        return schemaInfoList;
    }

    private void setSchemaInfoList(List<SchemaInfo> schemaInfoList) {

        this.schemaInfoList = schemaInfoList;
    }

    /**
     * Configuration builder class for the data sync client.
     * <p>
     * -DsourceVersion={version} - Source product version (Mandatory).
     * -DtargetVersion={version} - Target product version (Mandatory).
     * -DbatchSize={batch_size} - Size of a sync batch (Optional).
     * -DsyncInterval={sync_interval} - Interval between data sync batches (Optional).
     * -DsyncTables={TBL_1, TBL_2} - Tables to be synced (Mandatory).
     * -DumSchema={source_jndi,target_jndi} - JNDI names of target and source data sources for a um schema.
     * -DregSchema={source_jndi,target_jndi} - JNDI names of target and source data sources for a reg schema.
     * -DidentitySchema={source_jndi,target_jndi} - JNDI names of target and source data sources for a identity schema.
     * -DconsentSchema={source_jndi,target_jndi} - JNDI names of target and source data sources for a consent schema.
     */
    public static class ConfigurationBuilder {

        private Log log = LogFactory.getLog(ConfigurationBuilder.class);

        public Configuration build(Properties properties) throws SyncClientException {

            Configuration configuration = new Configuration();

            setVersionInfo(configuration, properties);
            setSchemaInfo(configuration, properties);
            setSyncTableList(configuration, properties);

            long syncInterval = DEFAULT_SYNC_INTERVAL;
            String syncIntervalStr = getProperty(JVM_PROPERTY_SYNC_INTERVAL, false, properties);
            try {
                if (StringUtils.isBlank(syncIntervalStr)) {
                    log.info("Using default sync interval: " + DEFAULT_SYNC_INTERVAL);
                } else {
                    syncInterval = Long.parseLong(syncIntervalStr.trim());
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid input: " + syncIntervalStr + " for sync interval. Using default sync interval: " +
                        DEFAULT_SYNC_INTERVAL);
            }

            configuration.setSyncInterval(syncInterval);

            int batchSize = DEFAULT_BATCH_SIZE;
            String batchSizeStr = getProperty(JVM_PROPERTY_BATCH_SIZE, false, properties);
            try {
                if (StringUtils.isBlank(batchSizeStr)) {
                    log.info("Using default batch size: " + DEFAULT_BATCH_SIZE);
                } else {
                    batchSize = Integer.parseInt(batchSizeStr.trim());
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid input: " + batchSizeStr + " for batch size. Using default batch size: " +
                        DEFAULT_BATCH_SIZE);
            }
            configuration.setBatchSize(batchSize);

            return configuration;
        }

        private void setSyncTableList(Configuration configuration, Properties properties) throws SyncClientException {

            String syncTables = getProperty(JVM_PROPERTY_SYNC_TABLES, true, properties);
            List<String> syncTableList = new ArrayList<>();
            if (syncTables != null) {
                String[] tables = syncTables.split(",");
                for (String table : tables) {
                    table = table.trim();
                    if (hasSchemaForTable(table, configuration)) {
                        syncTableList.add(table);
                    } else {
                        throw new SyncClientException("Could not find a matching schema for sync table: " + table);
                    }
                }
            }
            configuration.setSyncTables(syncTableList);
        }

        private boolean hasSchemaForTable(String tableName, Configuration configuration) {

            List<SchemaInfo> schemaInfoList = configuration.getSchemaInfoList();
            for (SchemaInfo schemaInfo : schemaInfoList) {
                if (schemaInfo.getTableList().contains(tableName)) {
                    return true;
                }
            }
            return false;
        }

        private void setSchemaInfo(Configuration configuration, Properties properties) throws SyncClientException {

            List<SchemaInfo> schemaInfoList = new ArrayList<>();
            SchemaInfo umSchemaInfo = getSchemaInfo(JVM_PROPERTY_UM_SCHEMA, SCHEMA_TYPE_UM, properties);
            SchemaInfo regSchemaInfo = getSchemaInfo(JVM_PROPERTY_REG_SCHEMA, SCHEMA_TYPE_REGISTRY, properties);
            SchemaInfo consentSchemaInfo = getSchemaInfo(JVM_PROPERTY_CONSENT_SCHEMA, SCHEMA_TYPE_CONSENT, properties);
            SchemaInfo identitySchemaInfo =
                    getSchemaInfo(JVM_PROPERTY_IDENTITY_SCHEMA, SCHEMA_TYPE_IDENTITY, properties);

            if (isNull(umSchemaInfo) && isNull(regSchemaInfo) && isNull(identitySchemaInfo) && isNull
                    (consentSchemaInfo)) {
                throw new SyncClientException(String.format("No input provided from schema info. At least one of %s, " +
                                "%s, %s, %s should be provided.", JVM_PROPERTY_UM_SCHEMA,
                        JVM_PROPERTY_REG_SCHEMA, JVM_PROPERTY_CONSENT_SCHEMA,
                        JVM_PROPERTY_IDENTITY_SCHEMA));
            }

            addToSchemaInfoList(schemaInfoList, umSchemaInfo);
            addToSchemaInfoList(schemaInfoList, regSchemaInfo);
            addToSchemaInfoList(schemaInfoList, identitySchemaInfo);
            addToSchemaInfoList(schemaInfoList, consentSchemaInfo);

            configuration.setSchemaInfoList(schemaInfoList);
        }

        private void addToSchemaInfoList(List<SchemaInfo> schemaInfoList, SchemaInfo schemaInfo) {

            if (nonNull(schemaInfo)) {
                schemaInfoList.add(schemaInfo);
            }
        }

        private SchemaInfo getSchemaInfo(String propertyName, String schemaType, Properties properties)
                throws SyncClientException {

            SchemaInfo schemaInfo = null;
            SchemaTableMapping mapping = new SchemaTableMapping();
            Map<String, List<String>> schemaTableMapping = mapping.getSchemaTableMapping();
            String schemaValue = getProperty(propertyName, false, properties);
            String sourceJndi;
            String targetJndi;

            if (StringUtils.isNotBlank(schemaValue)) {
                String[] split = schemaValue.split(",");
                if (split.length == 2) {
                    sourceJndi = split[0];
                    targetJndi = split[1];
                } else {
                    throw new SyncClientException("Property: " + propertyName + " should be in the format. " +
                            "-D" + propertyName + "=<source jndi name>,<target jndi name>");
                }
                if (StringUtils.isNotBlank(sourceJndi) && StringUtils.isNotBlank(targetJndi)) {
                    schemaInfo = new SchemaInfo(schemaType, sourceJndi.trim(), targetJndi.trim(),
                            schemaTableMapping.get(schemaType));
                } else {
                    throw new SyncClientException("<source jndi name> and <target jndi name> for Property: " +
                            propertyName + " cannot be empty. Example format: " +
                            "-D" + propertyName + "=<source jndi name>,<target jndi name>");

                }
            }
            return schemaInfo;
        }

        private void setVersionInfo(Configuration configuration, Properties properties) throws SyncClientException {

            String sourceVersion = getProperty(JVM_PROPERTY_SOURCE_VERSION, true, properties);
            String targetVersion = getProperty(JVM_PROPERTY_TARGET_VERSION, true, properties);

            configuration.setSourceVersion(sourceVersion.trim());
            configuration.setTargetVersion(targetVersion.trim());
        }

        private String getProperty(String propertyName, boolean mandatory, Properties properties)
                throws SyncClientException {

            String property = properties.getProperty(propertyName);
            if (StringUtils.isBlank(property)) {
                if (log.isDebugEnabled()) {
                    log.debug("Property " + propertyName + " is not available in file. Hence loading from system " +
                            "properties.");
                }
                property = System.getProperty(propertyName);
            }
            if (mandatory && StringUtils.isBlank(property)) {
                throw new SyncClientException("Mandatory system property: " + property + "is required for data " +
                        "synchronization operations.");
            }
            return property;
        }

    }

    public String getOldEncryptionAlgorithm() {

        return oldEncryptionAlgorithm;
    }

    public void setOldEncryptionAlgorithm(String oldEncryptionAlgorithm) {

        this.oldEncryptionAlgorithm = oldEncryptionAlgorithm;
    }
}
