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

import org.wso2.is.data.sync.system.exception.SyncClientException;
import org.wso2.is.data.sync.system.pipeline.JournalEntry;
import org.wso2.is.data.sync.system.pipeline.PipelineContext;
import org.wso2.is.data.sync.system.pipeline.transform.DataTransformer;
import org.wso2.is.data.sync.system.pipeline.transform.VersionAdvice;
import org.wso2.is.data.sync.system.pipeline.transform.model.TotpSecretDataInfo;
import org.wso2.is.data.sync.system.util.EncryptionUtil;
import org.wso2.is.data.sync.system.util.OAuth2Util;

import java.util.List;

import static org.wso2.is.data.sync.system.util.CommonUtil.getObjectValueFromEntry;
import static org.wso2.is.data.sync.system.util.CommonUtil.isIdentifierNamesMaintainedInLowerCase;
import static org.wso2.is.data.sync.system.util.Constant.COLUMN_DATA_KEY;
import static org.wso2.is.data.sync.system.util.Constant.COLUMN_DATA_VALUE;
import static org.wso2.is.data.sync.system.util.Constant.COLUMN_TENANT_ID;
import static org.wso2.is.data.sync.system.util.Constant.COLUMN_USER_NAME;
import static org.wso2.is.data.sync.system.util.OAuth2Util.updateJournalEntryForTotp;

@VersionAdvice(version = "5.11.0", tableName = "IDN_IDENTITY_USER_DATA")
public class TotpSecretJdbcDataStoreClaimDataTransformerV5110 implements DataTransformer {

    private String oldEncryptionAlgorithm;

    public TotpSecretJdbcDataStoreClaimDataTransformerV5110(String oldEncryptionAlgorithm) {

        this.oldEncryptionAlgorithm = oldEncryptionAlgorithm;
    }

    @Override
    public List<JournalEntry> transform(List<JournalEntry> journalEntryList, PipelineContext context)
            throws SyncClientException {

        String secretKeyIdentityClaim = "http://wso2.org/claims/identity/secretkey";
        String verifiedSecretKeyIdentityClaim = "http://wso2.org/claims/identity/verifySecretkey";

        boolean isColumnNameInsLowerCase = isIdentifierNamesMaintainedInLowerCase(context.getTargetConnection());
        for (JournalEntry entry : journalEntryList) {
            String secretKey = getObjectValueFromEntry(entry, COLUMN_DATA_VALUE,
                    isColumnNameInsLowerCase);
            String tenant = getObjectValueFromEntry(entry, COLUMN_TENANT_ID,
                    isColumnNameInsLowerCase);
            String userName = getObjectValueFromEntry(entry, COLUMN_USER_NAME,
                    isColumnNameInsLowerCase);
            String datakey = getObjectValueFromEntry(entry, COLUMN_DATA_KEY,
                    isColumnNameInsLowerCase);

            if (secretKeyIdentityClaim.equals(secretKey) || verifiedSecretKeyIdentityClaim.equals(secretKey)) {
                TotpSecretDataInfo totpSecretDataInfo = new TotpSecretDataInfo(tenant, userName, secretKey, datakey);
                EncryptionUtil.setCurrentEncryptionAlgorithm(oldEncryptionAlgorithm);

                OAuth2Util.transformTotpSecretsFromOldToNewEncryption(totpSecretDataInfo);
                updateJournalEntryForTotp(entry, totpSecretDataInfo, isColumnNameInsLowerCase);
            }

        }
        return journalEntryList;
    }
}
