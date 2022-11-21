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
package org.wso2.carbon.is.migration.util;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.identity.base.IdentityException;
import org.wso2.carbon.identity.core.migrate.MigrationClientException;
import org.wso2.carbon.identity.core.util.IdentityIOStreamUtils;
import org.wso2.carbon.is.migration.service.Migrator;
import org.wso2.carbon.user.api.Tenant;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class SecondaryUserStoreUtil {

    private static final Logger log = LoggerFactory.getLogger(SecondaryUserStoreUtil.class);

    public static void migrateSecondaryUserstorePasswords(Migrator migrator) throws MigrationClientException {

        try {
            updateSuperTenantConfigs();
            updateTenantConfigs(migrator);
        } catch (MigrationClientException e) {
            if (migrator.isContinueOnError()) {
                log.error("Error while migrating secondary userstore passwords.");
            } else {
                throw new MigrationClientException("Error while migrating secondary userstore passwords.", e);
            }
        }

    }

    public static void updateSuperTenantConfigs() {

        try {
            File[] userstoreConfigs = getUserStoreConfigFiles(Constant.SUPER_TENANT_ID);
            for (File file : userstoreConfigs) {
                if (file.isFile()) {
                    updatePassword(file.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            log.error("Error while updating secondary user store password for super tenant", e);
        }
    }

    public static void updateTenantConfigs(Migrator migrator) throws MigrationClientException {

        try {
            Set<Tenant> tenants = Utility.getTenants();
            for (Tenant tenant : tenants) {
                if (migrator.isIgnoreForInactiveTenants() && !tenant.isActive()) {
                    log.info("Tenant " + tenant.getDomain() + " is inactive. Skipping secondary userstore migration!");
                    continue;
                }
                try {
                    File[] userstoreConfigs = getUserStoreConfigFiles(tenant.getId());
                    for (File file : userstoreConfigs) {
                        if (file.isFile()) {
                            updatePassword(file.getAbsolutePath());
                        }
                    }
                } catch (FileNotFoundException | IdentityException e) {
                    String msg = "Error while updating secondary user store password for tenant: " + tenant.getDomain();
                    if (!migrator.isContinueOnError()) {
                        throw new MigrationClientException(msg, e);
                    }
                    log.error(msg, e);
                }
            }
        } catch (MigrationClientException e) {
            String msg = "Error while updating secondary user store password for tenant";
            if (!migrator.isContinueOnError()) {
                throw e;
            }
            log.error(msg, e);
        }
    }

    private static File[] getUserStoreConfigFiles(int tenantId) throws FileNotFoundException, IdentityException {

        String carbonHome = System.getProperty(Constant.CARBON_HOME);
        String userStorePath;
        if (tenantId == Constant.SUPER_TENANT_ID) {
            userStorePath = Paths.get(carbonHome, new String[]{"repository", "deployment", "server", "userstores"})
                    .toString();
        } else {
            userStorePath = Paths
                    .get(carbonHome, new String[]{"repository", "tenants", String.valueOf(tenantId), "userstores"})
                    .toString();
        }
        File[] files = new File(userStorePath).listFiles();
        return files != null ? files : new File[0];
    }

    private static void updatePassword(String filePath)
            throws FileNotFoundException, MigrationClientException {

        XMLStreamReader parser = null;
        FileInputStream stream = null;
        try {
            log.info("Migrating password in: " + filePath);
            stream = new FileInputStream(filePath);
            parser = XMLInputFactory.newInstance().createXMLStreamReader(stream);
            StAXOMBuilder builder = new StAXOMBuilder(parser);
            OMElement documentElement = builder.getDocumentElement();
            Iterator it = documentElement.getChildElements();
            String newEncryptedPassword = null;
            while (it.hasNext()) {
                OMElement element = (OMElement) it.next();
                if ("true".equals(element.getAttributeValue(new QName("encrypted"))) && (
                        "password".equals(element.getAttributeValue(new QName("name"))) || "ConnectionPassword"
                                .equals(element.getAttributeValue(new QName("name"))))) {
                    String encryptedPassword = element.getText();
                    newEncryptedPassword = EncryptionUtil.transformToSymmetric(encryptedPassword);
                    if (StringUtils.isNotEmpty(newEncryptedPassword)) {
                        element.setText(newEncryptedPassword);
                    }
                }
            }

            if (newEncryptedPassword != null) {
                OutputStream outputStream = new FileOutputStream(filePath);
                documentElement.serialize(outputStream);
            }
        } catch (XMLStreamException ex) {
            log.error("Error while updating password for: " + filePath, ex);
        } finally {
            try {
                if (parser != null) {
                    parser.close();
                }
                if (stream != null) {
                    IdentityIOStreamUtils.closeInputStream(stream);
                }
            } catch (XMLStreamException ex) {
                log.error("Error while closing XML stream", ex);
            }
        }
    }

}
