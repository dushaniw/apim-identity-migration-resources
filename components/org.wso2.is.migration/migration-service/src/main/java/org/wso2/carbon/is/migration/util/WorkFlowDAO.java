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
package org.wso2.carbon.is.migration.util;

import org.wso2.carbon.identity.core.migrate.MigrationClientException;
import org.wso2.carbon.identity.workflow.mgt.dto.WorkflowRequest;
import org.wso2.carbon.is.migration.service.Migrator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.wso2.carbon.is.migration.util.Constant.WORKFLOW_REQUEST_COLUMN;
import static org.wso2.carbon.is.migration.util.SQLConstants.GET_WORKFLOW_REQUEST_QUERY;
import static org.wso2.carbon.is.migration.util.SQLConstants.UPDATE_WORKFLOW_REQUEST_QUERY;

public class WorkFlowDAO {

    private static WorkFlowDAO instance = new WorkFlowDAO();

    private WorkFlowDAO() {

    }

    public static WorkFlowDAO getInstance() {

        return instance;
    }

    public List<WorkflowRequest> getAllWorkflowRequests(Migrator migrator) throws MigrationClientException {

        List<WorkflowRequest> workflowRequests = new ArrayList<>();
        ;
        try (Connection connection = migrator.getDataSource(Schema.IDENTITY.getName()).getConnection()) {

            connection.setAutoCommit(false);
            try (PreparedStatement preparedStatement = connection.prepareStatement(GET_WORKFLOW_REQUEST_QUERY);
                 ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    byte[] requestBytes = resultSet.getBytes(
                            WORKFLOW_REQUEST_COLUMN);
                    WorkflowRequest workflowRequest = deserializeWorkflowRequest(requestBytes);
                    workflowRequests.add(workflowRequest);
                }
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            throw new MigrationClientException("Error while retrieving work flow request objects.", e);
        }

        return workflowRequests;
    }

    private WorkflowRequest deserializeWorkflowRequest(byte[] serializedData) throws IOException,
            ClassNotFoundException {

        ByteArrayInputStream bais = new ByteArrayInputStream(serializedData);
        ObjectInputStream ois = new ObjectInputStream(bais);
        Object objectRead = ois.readObject();
        if (objectRead != null && objectRead instanceof WorkflowRequest) {
            return (WorkflowRequest) objectRead;
        }
        return null;
    }

    public void updateWorkFlowRequests(List<WorkflowRequest> updatedtworkFlowRequestList, Migrator migrator)
            throws MigrationClientException {

        try (Connection connection = migrator.getDataSource(Schema.IDENTITY.getName()).getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_WORKFLOW_REQUEST_QUERY)) {
                for (WorkflowRequest workflowRequest : updatedtworkFlowRequestList) {
                    preparedStatement.setBytes(1, serializeWorkflowRequest(workflowRequest));
                    preparedStatement.setString(2, workflowRequest.getUuid());
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
                connection.commit();
            } catch (SQLException | IOException e) {
                connection.rollback();
                throw new MigrationClientException("SQL error while updating workflow request objects. ", e);
            }
        } catch (SQLException e) {
            throw new MigrationClientException("Error while getting connection to WF_REQUEST table to " +
                    "update workflow request objects.", e);
        }

    }

    private byte[] serializeWorkflowRequest(WorkflowRequest workFlowRequest) throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(workFlowRequest);
        oos.close();
        return baos.toByteArray();
    }
}
