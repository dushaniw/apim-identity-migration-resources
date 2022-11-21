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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.identity.core.migrate.MigrationClientException;
import org.wso2.carbon.is.migration.service.Migrator;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class BPSProfileUtil {

    private static final Logger log = LoggerFactory.getLogger(BPSProfileUtil.class);

    public static void migrateBPSProfilePassword(Migrator migrator) throws MigrationClientException {

        List<BPSProfile> bpsProfileList = null;
        try {
            bpsProfileList = getBPSProfileList(migrator);
            List<BPSProfile> updatedBpsProfileList = transformPasswordFromOldToNewEncryption(bpsProfileList);
            updateBPSProfileList(migrator, updatedBpsProfileList);
        } catch (MigrationClientException e) {
            if (migrator.isContinueOnError()) {
                log.error("Error while migrating BPS profile password.");
            } else {
                throw new MigrationClientException("Error while migrating BPS profile password.", e);
            }
        }
    }

    public static List<BPSProfile> getBPSProfileList(Migrator migrator) throws MigrationClientException {

        List<BPSProfile> bpsProfileList;
        if (log.isDebugEnabled()) {
            log.debug("Retrieving BPS profile list.");
        }
        try {
            try (Connection connection = migrator.getDataSource().getConnection()) {
                connection.setAutoCommit(false);
                bpsProfileList = BPSProfileDAO.getInstance().getAllProfiles(connection);
            }
        } catch (SQLException e) {
            throw new MigrationClientException(
                    "Error while retrieving datasource or database connection for BPS profiles table", e);
        }
        return bpsProfileList;
    }

    public static void updateBPSProfileList(Migrator migrator, List<BPSProfile> updatedBpsProfileList)
            throws MigrationClientException {

        try (Connection connection = migrator.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            BPSProfileDAO.getInstance().updateNewPasswords(updatedBpsProfileList, connection);
        } catch (SQLException e) {
            throw new MigrationClientException("Error while updating newly encrypted BPS profile passwords.", e);
        }
    }

    private static List<BPSProfile> transformPasswordFromOldToNewEncryption(List<BPSProfile> bpsProfileList)
            throws MigrationClientException {

        List<BPSProfile> updatedBpsProfileList = new ArrayList<>();

        for (BPSProfile bpsProfile : bpsProfileList) {
            String newEncryptedPassword =
                    EncryptionUtil.transformToSymmetric(bpsProfile.getPassword());
            BPSProfile updatedBpsProfile = new BPSProfile(bpsProfile.getProfileName(), bpsProfile.getTenantId(),
                    newEncryptedPassword);
            updatedBpsProfileList.add(updatedBpsProfile);
        }
        return updatedBpsProfileList;
    }

}
