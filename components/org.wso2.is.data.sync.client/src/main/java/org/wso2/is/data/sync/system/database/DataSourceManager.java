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

package org.wso2.is.data.sync.system.database;

import org.apache.commons.lang.StringUtils;
import org.wso2.is.data.sync.system.config.Configuration;
import org.wso2.is.data.sync.system.exception.SyncClientException;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import static org.wso2.is.data.sync.system.util.Constant.DATA_SOURCE_TYPE_DB2;
import static org.wso2.is.data.sync.system.util.Constant.DATA_SOURCE_TYPE_H2;
import static org.wso2.is.data.sync.system.util.Constant.DATA_SOURCE_TYPE_MSSQL;
import static org.wso2.is.data.sync.system.util.Constant.DATA_SOURCE_TYPE_MYSQL;
import static org.wso2.is.data.sync.system.util.Constant.DATA_SOURCE_TYPE_ORACLE;
import static org.wso2.is.data.sync.system.util.Constant.DATA_SOURCE_TYPE_POSTGRESQL;
import static org.wso2.is.data.sync.system.util.Constant.DELIMITER;
import static org.wso2.is.data.sync.system.util.Constant.DELIMITER_COMMA;
import static org.wso2.is.data.sync.system.util.Constant.DELIMITER_DOUBLE_SLASH;
import static org.wso2.is.data.sync.system.util.Constant.SQL_DELIMITER_DB2;
import static org.wso2.is.data.sync.system.util.Constant.SQL_DELIMITER_H2_MYSQL_MSSQL_POSGRES;
import static org.wso2.is.data.sync.system.util.Constant.SQL_DELIMITER_ORACLE;

/**
 * DataSourceManager.
 */
public class DataSourceManager {

    private Map<String, DataSourceEntry> sourceEntryList = new HashMap<>();
    private Map<String, DataSourceEntry> targetEntryList = new HashMap<>();
    private List<SchemaInfo> schemaInfoList = new ArrayList<>();

    public DataSourceManager(Configuration configuration) throws SyncClientException {

        schemaInfoList = configuration.getSchemaInfoList();
        populateDataSourceEntryList(schemaInfoList, sourceEntryList, targetEntryList);
    }

    private void populateDataSourceEntryList(List<SchemaInfo> schemaInfoList,
                                             Map<String, DataSourceEntry> sourceEntryList,
                                             Map<String, DataSourceEntry> targetEntryList)
            throws SyncClientException {

        for (SchemaInfo schemaInfo : schemaInfoList) {

            String sourceJndiName = schemaInfo.getSourceJndiName();
            String targetJndiName = schemaInfo.getTargetJndiName();
            String schemaType = schemaInfo.getType();

            DataSource sourceDataSource = initializeDataSource(sourceJndiName);
            DataSource targetDataSource = initializeDataSource(targetJndiName);

            String sourceDataSourceType;
            try {
                sourceDataSourceType = getDataSourceType(sourceDataSource);
            } catch (SQLException e) {
                throw new SyncClientException("Error while creating connection with data source: " +
                        sourceJndiName + " of schema: " + schemaType);
            }

            String targetDataSourceType;
            try {
                targetDataSourceType = getDataSourceType(sourceDataSource);
            } catch (SQLException e) {
                throw new SyncClientException("Error while creating connection with data source: " +
                        targetJndiName + " of schema: " + schemaType);
            }

            sourceEntryList.put(schemaType, new DataSourceEntry(sourceDataSource, sourceDataSourceType));
            targetEntryList.put(schemaType, new DataSourceEntry(targetDataSource, targetDataSourceType));
        }
    }

    private DataSource initializeDataSource(String jndiName) throws SyncClientException {

        DataSource dataSource;
        try {
            InitialContext ctx = new InitialContext();
            dataSource = (DataSource) ctx.lookup(jndiName);

        } catch (NamingException e) {
            throw new SyncClientException("Error while data source lookup for: " + jndiName);
        }
        return dataSource;
    }

    private String getDataSourceType(DataSource dataSource) throws SQLException, SyncClientException {

        String type;
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String databaseProductName = metaData.getDatabaseProductName();
            if (databaseProductName.matches("(?i).*" + DATA_SOURCE_TYPE_MYSQL + ".*")) {
                type = DATA_SOURCE_TYPE_MYSQL;
            } else if (databaseProductName.matches("(?i).*" + DATA_SOURCE_TYPE_ORACLE + ".*")) {
                type = DATA_SOURCE_TYPE_ORACLE;
            } else if (databaseProductName.matches("(?i).*" + DATA_SOURCE_TYPE_MSSQL + ".*")) {
                type = DATA_SOURCE_TYPE_MSSQL;
            } else if (databaseProductName.matches("(?i).*" + DATA_SOURCE_TYPE_H2 + ".*")) {
                type = DATA_SOURCE_TYPE_H2;
            } else if (databaseProductName.matches("(?i).*" + DATA_SOURCE_TYPE_DB2 + ".*")) {
                type = DATA_SOURCE_TYPE_DB2;
            } else if (databaseProductName.matches("(?i).*" + DATA_SOURCE_TYPE_POSTGRESQL + ".*")) {
                type = DATA_SOURCE_TYPE_POSTGRESQL;
            } else {
                throw new SyncClientException("Unsupported data source type: " + databaseProductName);
            }
        }
        return type;
    }

    public String getSourceDataSourceType(String schema) {

        return sourceEntryList.get(schema).getType();
    }

    public String getTargetDataSourceType(String schema) {

        return targetEntryList.get(schema).getType();
    }

    public DataSource getSourceDataSource(String schema) {

        return sourceEntryList.get(schema).getDataSource();
    }

    public DataSource getTargetDataSource(String schema) {

        return targetEntryList.get(schema).getDataSource();
    }

    public Connection getSourceConnection(String schema) throws SQLException {

        DataSource dataSource = sourceEntryList.get(schema).getDataSource();
        return dataSource.getConnection();
    }

    public Connection getTargetConnection(String schema) throws SQLException {

        DataSource dataSource = targetEntryList.get(schema).getDataSource();
        return dataSource.getConnection();
    }

    public String getSourceSqlDelimiter(String schema) {

        String dataSourceType = getSourceDataSourceType(schema);
        if (DATA_SOURCE_TYPE_ORACLE.equals(dataSourceType)) {
            return SQL_DELIMITER_ORACLE;
        } else if (DATA_SOURCE_TYPE_DB2.equals(dataSourceType)) {
            return SQL_DELIMITER_DB2;
        }
        return SQL_DELIMITER_H2_MYSQL_MSSQL_POSGRES;
    }

    public String getTargetSqlDelimiter(String schema) {

        String dataSourceType = getTargetDataSourceType(schema);
        if (DATA_SOURCE_TYPE_ORACLE.equals(dataSourceType)) {
            return SQL_DELIMITER_ORACLE;
        } else if (DATA_SOURCE_TYPE_DB2.equals(dataSourceType)) {
            return SQL_DELIMITER_DB2;
        }
        return SQL_DELIMITER_H2_MYSQL_MSSQL_POSGRES;
    }

    public String getSourceDDLPrefix(String schema) {

        if (DATA_SOURCE_TYPE_MYSQL.equals(getSourceDataSourceType(schema))) {

            return DELIMITER + " " + DELIMITER_DOUBLE_SLASH + System.lineSeparator() + System.lineSeparator();
        }
        return StringUtils.EMPTY;
    }

    public String getTargetDDLPrefix(String schema) {

        if (DATA_SOURCE_TYPE_MYSQL.equals(getTargetDataSourceType(schema))) {

            return DELIMITER + " " + DELIMITER_DOUBLE_SLASH + System.lineSeparator() + System.lineSeparator();
        }
        return StringUtils.EMPTY;
    }

    public String getSourceDDLSuffix(String schema) {

        if (DATA_SOURCE_TYPE_MYSQL.equals(getSourceDataSourceType(schema))) {
            return System.lineSeparator() + System.lineSeparator() + DELIMITER + " " + DELIMITER_COMMA;
        }
        return StringUtils.EMPTY;
    }

    public String getTargetDDLSuffix(String schema) {

        if (DATA_SOURCE_TYPE_MYSQL.equals(getTargetDataSourceType(schema))) {
            return System.lineSeparator() + System.lineSeparator() + DELIMITER + " " + DELIMITER_COMMA;
        }
        return StringUtils.EMPTY;
    }

    public String getSchema(String tableName) throws SyncClientException {

        for (SchemaInfo schemaInfo : schemaInfoList) {

            if (isTableExistInSchemaInfo(tableName, schemaInfo)) {
                return schemaInfo.getType();
            }
        }
        throw new SyncClientException("Could not find a matching schema for: " + tableName);
    }

    private boolean isTableExistInSchemaInfo(String tableName, SchemaInfo schemaInfo) {

        return schemaInfo.getTableList().stream().anyMatch(s -> s.equalsIgnoreCase(tableName));
    }
}
