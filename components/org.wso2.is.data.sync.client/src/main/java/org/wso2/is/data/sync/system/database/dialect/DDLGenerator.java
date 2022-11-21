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

package org.wso2.is.data.sync.system.database.dialect;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.is.data.sync.system.database.ColumnData;
import org.wso2.is.data.sync.system.database.DataSourceManager;
import org.wso2.is.data.sync.system.database.SQLStatement;
import org.wso2.is.data.sync.system.database.TableMetaData;
import org.wso2.is.data.sync.system.database.dialect.impl.DB2DatabaseDialect;
import org.wso2.is.data.sync.system.database.dialect.impl.H2DatabaseDialect;
import org.wso2.is.data.sync.system.database.dialect.impl.MSSQLDatabaseDialect;
import org.wso2.is.data.sync.system.database.dialect.impl.MySQLDatabaseDialect;
import org.wso2.is.data.sync.system.database.dialect.impl.OracleDatabaseDialect;
import org.wso2.is.data.sync.system.database.dialect.impl.PostgreSQLDatabaseDialect;
import org.wso2.is.data.sync.system.exception.SyncClientException;
import org.wso2.is.data.sync.system.util.Constant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import static org.wso2.is.data.sync.system.util.CommonUtil.getColumnData;
import static org.wso2.is.data.sync.system.util.CommonUtil.getDeleteTriggerName;
import static org.wso2.is.data.sync.system.util.CommonUtil.getDeleteTriggerNameForChildTable;
import static org.wso2.is.data.sync.system.util.CommonUtil.getInsertTriggerName;
import static org.wso2.is.data.sync.system.util.CommonUtil.getPrimaryKeys;
import static org.wso2.is.data.sync.system.util.CommonUtil.getSyncTableName;
import static org.wso2.is.data.sync.system.util.CommonUtil.getSyncVersionTableName;
import static org.wso2.is.data.sync.system.util.CommonUtil.getUpdateTriggerName;
import static org.wso2.is.data.sync.system.util.Constant.COLUMN_NAME_ACTION;
import static org.wso2.is.data.sync.system.util.Constant.COLUMN_NAME_SYNC_ID;
import static org.wso2.is.data.sync.system.util.Constant.COLUMN_TYPE_INT;
import static org.wso2.is.data.sync.system.util.Constant.COLUMN_TYPE_VARCHAR;
import static org.wso2.is.data.sync.system.util.Constant.DATA_SOURCE_TYPE_DB2;
import static org.wso2.is.data.sync.system.util.Constant.DATA_SOURCE_TYPE_H2;
import static org.wso2.is.data.sync.system.util.Constant.DATA_SOURCE_TYPE_MSSQL;
import static org.wso2.is.data.sync.system.util.Constant.DATA_SOURCE_TYPE_MYSQL;
import static org.wso2.is.data.sync.system.util.Constant.DATA_SOURCE_TYPE_ORACLE;
import static org.wso2.is.data.sync.system.util.Constant.DATA_SOURCE_TYPE_POSTGRESQL;
import static org.wso2.is.data.sync.system.util.Constant.FK_COLUMN_NAME;
import static org.wso2.is.data.sync.system.util.Constant.PK_COLUMN_NAME;
import static org.wso2.is.data.sync.system.util.Constant.PK_TABLE_NAME;
import static org.wso2.is.data.sync.system.util.Constant.SELECTION_POLICY_FOR_EACH_ROW;
import static org.wso2.is.data.sync.system.util.Constant.SQL_STATEMENT_TYPE_SOURCE;
import static org.wso2.is.data.sync.system.util.Constant.SQL_STATEMENT_TYPE_TARGET;
import static org.wso2.is.data.sync.system.util.Constant.SYNC_OPERATION_DELETE;
import static org.wso2.is.data.sync.system.util.Constant.SYNC_OPERATION_INSERT;
import static org.wso2.is.data.sync.system.util.Constant.SYNC_OPERATION_UPDATE;
import static org.wso2.is.data.sync.system.util.Constant.TRIGGER_TIMING_AFTER;

/**
 * Generates DDLs triggers and tables for data syncing.
 */
public class DDLGenerator {

    private List<String> syncTableList;
    private DataSourceManager dataSourceManager;
    private Map<String, DatabaseDialect> databaseDialectMap = new HashMap<>();
    private Log log = LogFactory.getLog(DDLGenerator.class);

    public DDLGenerator(List<String> syncTableList, DataSourceManager dataSourceManager) {

        this.syncTableList = syncTableList;
        this.dataSourceManager = dataSourceManager;

        databaseDialectMap.put(DATA_SOURCE_TYPE_MYSQL, new MySQLDatabaseDialect());
        databaseDialectMap.put(DATA_SOURCE_TYPE_H2, new H2DatabaseDialect());
        databaseDialectMap.put(DATA_SOURCE_TYPE_ORACLE, new OracleDatabaseDialect());
        databaseDialectMap.put(DATA_SOURCE_TYPE_MSSQL, new MSSQLDatabaseDialect());
        databaseDialectMap.put(DATA_SOURCE_TYPE_DB2, new DB2DatabaseDialect());
        databaseDialectMap.put(DATA_SOURCE_TYPE_POSTGRESQL, new PostgreSQLDatabaseDialect());
    }

    public void generateScripts(boolean ddlOnly) throws SyncClientException {
        List<SQLStatement> sqlStatementList = generateSyncScripts();

        Map<String, List<SQLStatement>> sourceStatements = new LinkedHashMap<>();
        Map<String, List<SQLStatement>> targetStatements = new LinkedHashMap<>();
        for (SQLStatement sqlStatement : sqlStatementList) {

            if (SQL_STATEMENT_TYPE_SOURCE.equals(sqlStatement.getType())) {
                addToStatementMap(sourceStatements, sqlStatement);
            } else {
                addToStatementMap(targetStatements, sqlStatement);
            }
        }

        if (ddlOnly) {

            for (String schema : sourceStatements.keySet()) {
                String sqlDelimiter = dataSourceManager.getSourceSqlDelimiter(schema);
                String ddlPrefix = dataSourceManager.getSourceDDLPrefix(schema);
                String ddlSuffix = dataSourceManager.getSourceDDLSuffix(schema);
                writeSqlFile(sourceStatements, schema, sqlDelimiter, ddlPrefix, ddlSuffix, SQL_STATEMENT_TYPE_SOURCE);
            }

            for (String schema : targetStatements.keySet()) {
                String sqlDelimiter = dataSourceManager.getTargetSqlDelimiter(schema);
                String ddlPrefix = dataSourceManager.getTargetDDLPrefix(schema);
                String ddlSuffix = dataSourceManager.getTargetDDLSuffix(schema);
                writeSqlFile(targetStatements, schema, sqlDelimiter, ddlPrefix, ddlSuffix, SQL_STATEMENT_TYPE_TARGET);
            }
        } else {

            for (Map.Entry<String, List<SQLStatement>> entry : sourceStatements.entrySet()) {
                try (Connection connection = dataSourceManager.getSourceConnection(entry.getKey())) {
                    executeDDLOnDataSource(entry.getKey(), entry.getValue(), connection, SQL_STATEMENT_TYPE_SOURCE);
                } catch (SQLException e) {
                    throw new SyncClientException(
                            "Error while creating a connection on " + SQL_STATEMENT_TYPE_SOURCE + " schema: " +
                                    entry.getKey(), e);
                }
            }

            for (Map.Entry<String, List<SQLStatement>> entry : targetStatements.entrySet()) {
                try (Connection connection = dataSourceManager.getTargetConnection(entry.getKey())) {
                    executeDDLOnDataSource(entry.getKey(), entry.getValue(), connection, SQL_STATEMENT_TYPE_TARGET);
                } catch (SQLException e) {
                    throw new SyncClientException(
                            "Error while creating a connection on " + SQL_STATEMENT_TYPE_TARGET + " schema: " +
                                    entry.getKey(), e);
                }
            }
        }
    }

    private void executeDDLOnDataSource(String schema, List<SQLStatement> sqlStatements, Connection connection,
                                        String sqlStatementType) throws SQLException, SyncClientException {

        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            for (SQLStatement sqlStatement : sqlStatements) {
                log.info("Queuing " +
                        sqlStatementType + " statement for batch operation: " + sqlStatement.getStatement());
                statement.addBatch(sqlStatement.getStatement());
            }
            statement.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw new SyncClientException(
                    "Error while executing SQL statements on " + sqlStatementType + " schema: " +
                            schema, e);
        }
    }

    private void writeSqlFile(Map<String, List<SQLStatement>> sourceStatements, String schema, String sqlDelimiter,
                              String ddlPrefix, String ddlSuffix, String dataSourceType) throws SyncClientException {

        String delimiter = sqlDelimiter + System.lineSeparator() + System.lineSeparator();
        StringJoiner joiner = new StringJoiner(delimiter, ddlPrefix, sqlDelimiter + ddlSuffix);

        List<SQLStatement> sqlStatements = sourceStatements.get(schema);
        for (SQLStatement sqlStatement : sqlStatements) {
            joiner.add(sqlStatement.getStatement());
        }

        String script = joiner.toString();

        String scriptFileName = schema + "_" + dataSourceType + Constant.SQL_FILE_EXTENSION;
        Path path = Paths.get(Constant.DBSCRIPTS_LOCATION, Constant.SYNC_TOOL_SCRIPT_LOCATION, scriptFileName);
        log.info("Writing file to: " + path.toAbsolutePath());

        byte[] strToBytes = script.getBytes();

        try {
            Files.createDirectories(path.getParent());
            Files.write(path, strToBytes);
        } catch (IOException e) {
            throw new SyncClientException("Error while generating script: " + path.toString(), e);
        }
    }

    private void addToStatementMap(Map<String, List<SQLStatement>> statements, SQLStatement sqlStatement) {

        if (statements.containsKey(sqlStatement.getScheme())) {
            statements.get(sqlStatement.getScheme()).add(sqlStatement);
        } else {
            statements.put(sqlStatement.getScheme(), new ArrayList<>(Collections.singleton(sqlStatement)));
        }
    }

    public List<SQLStatement> generateSyncScripts() throws SyncClientException {

        List<SQLStatement> scripts = new ArrayList<>();

        scripts.addAll(dropTables());
        scripts.addAll(generateTables());
        scripts.addAll(generateTriggers());

        return scripts;
    }

    public List<SQLStatement> generateTriggers() throws SyncClientException {

        List<SQLStatement> sqlStatementList = new ArrayList<>();
        for (String tableName : syncTableList) {
            String schema = dataSourceManager.getSchema(tableName);
            String dataSourceType = dataSourceManager.getSourceDataSourceType(schema);
            DatabaseDialect databaseDialect = databaseDialectMap.get(dataSourceType);
            try (Connection sourceConnection = dataSourceManager.getSourceConnection(schema)) {
                TableMetaData tableMetaData = new TableMetaData.Builder().setColumnData(
                        getColumnData(tableName, sourceConnection)).setPrimaryKeys(
                        getPrimaryKeys(tableName, sourceConnection)).build();

                String targetTableName = getSyncTableName(tableName);

                String insertTriggerName = getInsertTriggerName(tableName);
                String updateTriggerName = getUpdateTriggerName(tableName);
                String deleteTriggerName = getDeleteTriggerName(tableName);

                Trigger onInsertTrigger = new Trigger(insertTriggerName, tableName, targetTableName,
                        SYNC_OPERATION_INSERT, tableMetaData,
                        SELECTION_POLICY_FOR_EACH_ROW, TRIGGER_TIMING_AFTER);
                Trigger onUpdateTrigger = new Trigger(updateTriggerName, tableName, targetTableName,
                        SYNC_OPERATION_UPDATE, tableMetaData,
                        SELECTION_POLICY_FOR_EACH_ROW, TRIGGER_TIMING_AFTER);
                Trigger onDeleteTrigger = new Trigger(deleteTriggerName, tableName, targetTableName,
                        SYNC_OPERATION_DELETE, tableMetaData, SELECTION_POLICY_FOR_EACH_ROW, TRIGGER_TIMING_AFTER);

                List<String> dropInsertTriggerSQL = databaseDialect.generateDropTrigger(insertTriggerName, targetTableName);
                List<String> dropUpdateTriggerSQL = databaseDialect.generateDropTrigger(updateTriggerName, targetTableName);
                List<String> dropDeleteTriggerSQL = databaseDialect.generateDropTrigger(deleteTriggerName, targetTableName);
                List<String> onInsertTriggerSQL = databaseDialect.generateCreateTrigger(onInsertTrigger);
                List<String> onUpdateTriggerSQL = databaseDialect.generateCreateTrigger(onUpdateTrigger);
                List<String> onDeleteTriggerSQL = databaseDialect.generateCreateTrigger(onDeleteTrigger);

                addStatementsToStatementList(schema, SQL_STATEMENT_TYPE_SOURCE, sqlStatementList, dropInsertTriggerSQL);
                addStatementsToStatementList(schema, SQL_STATEMENT_TYPE_SOURCE, sqlStatementList, dropUpdateTriggerSQL);
                addStatementsToStatementList(schema, SQL_STATEMENT_TYPE_SOURCE, sqlStatementList, dropDeleteTriggerSQL);
                addStatementsToStatementList(schema, SQL_STATEMENT_TYPE_SOURCE, sqlStatementList, onInsertTriggerSQL);
                addStatementsToStatementList(schema, SQL_STATEMENT_TYPE_SOURCE, sqlStatementList, onUpdateTriggerSQL);
                addStatementsToStatementList(schema, SQL_STATEMENT_TYPE_SOURCE, sqlStatementList, onDeleteTriggerSQL);

                DatabaseMetaData metaData = sourceConnection.getMetaData();

                try (ResultSet rs = metaData.getImportedKeys(sourceConnection.getCatalog(), null, tableName)) {
                    int triggerCount = 0;
                    List<String> dropDeleteTriggerListSQLForChildTables = new ArrayList<>();
                    List<String> onDeleteTriggerListForChildTables = new ArrayList<>();

                    while (rs.next()) {
                        String parentTableName = rs.getString(PK_TABLE_NAME);
                        String pkColumnName = rs.getString(PK_COLUMN_NAME);
                        String fkColumnName = rs.getString(FK_COLUMN_NAME);
                        String triggerName = getDeleteTriggerNameForChildTable(tableName, triggerCount);

                        Map<String, String> columnIds = new HashMap<>();
                        columnIds.put(pkColumnName, fkColumnName);

                        if (parentTableName != null && syncTableList.contains(parentTableName)) {

                            List<String> dropTriggerList =
                                    databaseDialect.generateDropTrigger(triggerName, targetTableName);
                            if (dropTriggerList != null) {
                                dropDeleteTriggerListSQLForChildTables.addAll(dropTriggerList);
                            }

                            Trigger onDeleteTriggersForChildTables = new Trigger(triggerName, parentTableName,
                                    targetTableName, SYNC_OPERATION_DELETE, tableMetaData,
                                    SELECTION_POLICY_FOR_EACH_ROW, TRIGGER_TIMING_AFTER);
                            List<String> deleteTriggerList =
                                    databaseDialect.generateDeleteTrigger(onDeleteTriggersForChildTables, columnIds);
                            if (deleteTriggerList != null) {
                                onDeleteTriggerListForChildTables.addAll(deleteTriggerList);
                            }
                        }
                        triggerCount++;
                    }
                    addStatementsToStatementList(schema, SQL_STATEMENT_TYPE_SOURCE, sqlStatementList,
                                dropDeleteTriggerListSQLForChildTables);
                    addStatementsToStatementList(schema, SQL_STATEMENT_TYPE_SOURCE, sqlStatementList,
                            onDeleteTriggerListForChildTables);

                }
            } catch (SQLException e) {
                throw new SyncClientException("Error occurred while creating connection for source schema: " + schema);
            }
        }
        return sqlStatementList;
    }

    private void addStatementsToStatementList(String schema, String statementType, List<SQLStatement> sqlStatementList,
                                              List<String> statements) {

        if (Objects.nonNull(statements)) {
            statements.forEach(s -> sqlStatementList.add(new SQLStatement(schema, s, statementType)));
        }
    }

    public List<SQLStatement> generateTables() throws SyncClientException {

        List<SQLStatement> sqlStatementList = new ArrayList<>();
        for (String tableName : syncTableList) {

            String schema = dataSourceManager.getSchema(tableName);
            String dataSourceType = dataSourceManager.getSourceDataSourceType(schema);
            DatabaseDialect databaseDialect = databaseDialectMap.get(dataSourceType);

            List<String> createSyncTableSQL = getCreateSyncTableStatement(tableName, schema, databaseDialect);
            List<String> createSyncVersionTableStatement = getCreateSyncVersionTableStatement(tableName,
                    databaseDialect);

            addStatementsToStatementList(schema, SQL_STATEMENT_TYPE_SOURCE, sqlStatementList, createSyncTableSQL);
            addStatementsToStatementList(schema, SQL_STATEMENT_TYPE_TARGET, sqlStatementList,
                    createSyncVersionTableStatement);
        }
        return sqlStatementList;
    }

    public List<SQLStatement> dropTriggers() throws SyncClientException {

        List<SQLStatement> sqlStatementList = new ArrayList<>();
        for (String tableName : syncTableList) {

            String schema = dataSourceManager.getSchema(tableName);
            String dataSourceType = dataSourceManager.getSourceDataSourceType(schema);
            DatabaseDialect databaseDialect = databaseDialectMap.get(dataSourceType);

            String insertTriggerName = getInsertTriggerName(tableName);
            String updateTriggerName = getUpdateTriggerName(tableName);
            String deleteTriggerName = getDeleteTriggerName(tableName);

            List<String> dropInsertTriggerSQL = databaseDialect.generateDropTrigger(insertTriggerName, tableName);
            List<String> dropUpdateTriggerSQL = databaseDialect.generateDropTrigger(updateTriggerName, tableName);
            List<String> dropDeleteTriggerSQL = databaseDialect.generateDropTrigger(deleteTriggerName, tableName);

            addStatementsToStatementList(schema, SQL_STATEMENT_TYPE_SOURCE, sqlStatementList, dropInsertTriggerSQL);
            addStatementsToStatementList(schema, SQL_STATEMENT_TYPE_SOURCE, sqlStatementList, dropUpdateTriggerSQL);
            addStatementsToStatementList(schema, SQL_STATEMENT_TYPE_SOURCE, sqlStatementList, dropDeleteTriggerSQL);

        }
        return sqlStatementList;
    }

    public List<SQLStatement> dropTables() throws SyncClientException {

        List<SQLStatement> sqlStatementList = new ArrayList<>();
        for (String tableName : syncTableList) {

            String schema = dataSourceManager.getSchema(tableName);
            String dataSourceType = dataSourceManager.getSourceDataSourceType(schema);
            DatabaseDialect databaseDialect = databaseDialectMap.get(dataSourceType);
            String syncTableName = getSyncTableName(tableName);
            String syncVersionTableName = getSyncVersionTableName(tableName);

            List<String> dropSyncTableSQL = databaseDialect.generateDropTable(syncTableName);
            List<String> dropSyncVersionTableSQL = databaseDialect.generateDropTable(syncVersionTableName);

            addStatementsToStatementList(schema, SQL_STATEMENT_TYPE_SOURCE, sqlStatementList, dropSyncTableSQL);
            addStatementsToStatementList(schema, SQL_STATEMENT_TYPE_TARGET, sqlStatementList, dropSyncVersionTableSQL);
        }
        return sqlStatementList;
    }

    private List<String> getCreateSyncTableStatement(String tableName, String schema, DatabaseDialect databaseDialect)
            throws SyncClientException {

        try (Connection sourceConnection = dataSourceManager.getSourceConnection(schema)) {

            List<ColumnData> columnData = getColumnData(tableName, sourceConnection);
            ColumnData syncIdColumn = new ColumnData(COLUMN_NAME_SYNC_ID, COLUMN_TYPE_INT, 11);
            syncIdColumn.setAutoIncrement(true);
            columnData.add(syncIdColumn);

            ColumnData actionColumn = new ColumnData(COLUMN_NAME_ACTION, COLUMN_TYPE_VARCHAR, 15);
            actionColumn.setAutoIncrement(false);
            columnData.add(actionColumn);

            List<String> primaryKeys = Collections.singletonList(COLUMN_NAME_SYNC_ID);
            TableMetaData tableMetaData = new TableMetaData.Builder().setColumnData(columnData)
                    .setPrimaryKeys(primaryKeys)
                    .build();
            String syncTableName = getSyncTableName(tableName);
            Table table = new Table(syncTableName, tableMetaData);
            return databaseDialect.generateCreateTable(table);
        } catch (SQLException e) {
            throw new SyncClientException("Error occurred while creating connection for source schema: " + schema);
        }
    }

    private List<String> getCreateSyncVersionTableStatement(String tableName, DatabaseDialect databaseDialect)
            throws SyncClientException {

        List<ColumnData> columnData = new ArrayList<>();
        ColumnData syncIdColumn = new ColumnData(COLUMN_NAME_SYNC_ID, COLUMN_TYPE_INT, 11);
        columnData.add(syncIdColumn);
        List<String> primaryKeys = Collections.singletonList(COLUMN_NAME_SYNC_ID);
        TableMetaData tableMetaData = new TableMetaData.Builder().setColumnData(columnData)
                .setPrimaryKeys(primaryKeys)
                .build();
        String syncVersionTableName = getSyncVersionTableName(tableName);
        Table table = new Table(syncVersionTableName, tableMetaData);

        return databaseDialect.generateCreateTable(table);
    }
}
