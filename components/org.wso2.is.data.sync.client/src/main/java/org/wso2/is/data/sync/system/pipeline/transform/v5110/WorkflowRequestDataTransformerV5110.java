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
package org.wso2.is.data.sync.system.pipeline.transform.v5110;

import org.wso2.carbon.identity.workflow.mgt.dto.WorkflowRequest;
import org.wso2.is.data.sync.system.exception.SyncClientException;
import org.wso2.is.data.sync.system.pipeline.JournalEntry;
import org.wso2.is.data.sync.system.pipeline.PipelineContext;
import org.wso2.is.data.sync.system.pipeline.transform.DataTransformer;
import org.wso2.is.data.sync.system.pipeline.transform.VersionAdvice;
import org.wso2.is.data.sync.system.util.EncryptionUtil;
import org.wso2.is.data.sync.system.util.WorkFlowUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

import static org.wso2.is.data.sync.system.util.CommonUtil.getObjectValueFromEntry;
import static org.wso2.is.data.sync.system.util.CommonUtil.isIdentifierNamesMaintainedInLowerCase;
import static org.wso2.is.data.sync.system.util.Constant.COLUMN_REQUEST;
import static org.wso2.is.data.sync.system.util.Constant.COLUMN_UUID;

@VersionAdvice(version = "5.11.0", tableName = "WF_REQUEST")
public class WorkflowRequestDataTransformerV5110 implements DataTransformer {

    private String oldEncryptionAlgorithm;

    public WorkflowRequestDataTransformerV5110(String oldEncryptionAlgorithmConfigured) {

        this.oldEncryptionAlgorithm = oldEncryptionAlgorithmConfigured;
    }

    @Override
    public List<JournalEntry> transform(List<JournalEntry> journalEntryList, PipelineContext context)
            throws SyncClientException {

        boolean isColumnNameInsLowerCase = isIdentifierNamesMaintainedInLowerCase(context.getTargetConnection());
        for (JournalEntry entry : journalEntryList) {

            byte[] workFlowRequest = getObjectValueFromEntry(entry, COLUMN_REQUEST,
                    isColumnNameInsLowerCase);
            String workFlowUuid = getObjectValueFromEntry(entry, COLUMN_UUID,
                    isColumnNameInsLowerCase);
            WorkflowRequest workflowRequest = deserializeWorkflowRequest(workFlowRequest);
            EncryptionUtil.setCurrentEncryptionAlgorithm(oldEncryptionAlgorithm);
            WorkFlowUtil.transformWorkFlowCredentialsFromOldToNewEncryption(workflowRequest);
        }
        return journalEntryList;
    }

    private WorkflowRequest deserializeWorkflowRequest(byte[] serializedData) throws SyncClientException {

        ByteArrayInputStream bais = new ByteArrayInputStream(serializedData);
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(bais);

            Object objectRead = ois.readObject();
            if (objectRead != null && objectRead instanceof WorkflowRequest) {
                return (WorkflowRequest) objectRead;
            }

        } catch (IOException | ClassNotFoundException e) {
            throw new SyncClientException("Error while deserializing workflow request object.",e);
        }
        return null;
    }
}
