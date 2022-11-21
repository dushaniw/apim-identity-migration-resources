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
package org.wso2.carbon.is.migration.service;

import com.google.gson.Gson;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.core.migrate.MigrationClientException;
import org.wso2.carbon.identity.core.persistence.registry.RegistryResourceMgtService;
import org.wso2.carbon.identity.core.persistence.registry.RegistryResourceMgtServiceImpl;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.governance.model.NotificationTemplate;
import org.wso2.carbon.is.migration.util.Constant;
import org.wso2.carbon.is.migration.util.Utility;
import org.wso2.carbon.registry.core.Collection;
import org.wso2.carbon.registry.core.CollectionImpl;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.ResourceImpl;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.user.api.Tenant;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import static org.wso2.carbon.base.MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;
import static org.wso2.carbon.registry.core.RegistryConstants.PATH_SEPARATOR;

/**
 * Migration implementation for email template.
 */
public class EmailTemplateDataMigrator extends Migrator {

    private static final Logger log = LoggerFactory.getLogger(EmailTemplateDataMigrator.class);
    private static final String EMAIL_ADMIN_CONFIG = "email-admin-config.xml";
    private RegistryResourceMgtService registryResourceMgtService = new RegistryResourceMgtServiceImpl();

    private static Map<String, String> getEmailContent(OMElement templateElement) {

        Map<String, String> emailContentMap = new HashMap<>();
        Iterator it = templateElement.getChildElements();
        while (it.hasNext()) {
            OMElement element = (OMElement) it.next();
            String elementName = element.getLocalName();
            String elementText = element.getText();
            if (StringUtils.equalsIgnoreCase(Constant.TEMPLATE_SUBJECT, elementName)) {
                emailContentMap.put(Constant.TEMPLATE_SUBJECT, elementText);
            } else if (StringUtils.equalsIgnoreCase(Constant.TEMPLATE_BODY, elementName)) {
                emailContentMap.put(Constant.TEMPLATE_BODY, elementText);
            } else if (StringUtils.equalsIgnoreCase(Constant.TEMPLATE_FOOTER, elementName)) {
                emailContentMap.put(Constant.TEMPLATE_FOOTER, elementText);
            }
        }
        return emailContentMap;
    }

    private static String getNormalizedName(String displayName) {

        if (StringUtils.isNotBlank(displayName)) {
            return displayName.replaceAll("\\s+", "").toLowerCase();
        }
        throw new IllegalArgumentException("Invalid template type name provided : " + displayName);
    }

    private static Collection createTemplateType(String normalizedTemplateName, String templateDisplayName) {

        Collection collection = new CollectionImpl();
        collection.addProperty(Constant.EMAIL_TEMPLATE_NAME, normalizedTemplateName);
        collection.addProperty(Constant.EMAIL_TEMPLATE_TYPE_DISPLAY_NAME, templateDisplayName);
        return collection;
    }

    @Override
    public void dryRun() throws MigrationClientException {

        log.info("Dry run capability not implemented in {} migrator.", this.getClass().getName());
    }

    @Override
    public void migrate() throws MigrationClientException {

        List<NotificationTemplate> emailTemplates = loadEmailTemplates();
        try {
            // Migrate super tenant.
            boolean skipSuperTenantMigration = Boolean.parseBoolean(
                    getMigratorConfig().getParameterValue(Constant.SKIP_SUPER_TENANT_EMAIL_TEMPLATE_MIGRATION));
            if (!skipSuperTenantMigration) {
                migrateTenantEmailTemplates(emailTemplates, SUPER_TENANT_DOMAIN_NAME);
            }

            // Migrate other tenants.
            Set<Tenant> tenants = Utility.getTenants();
            List<Integer> inactiveTenants = Utility.getInactiveTenants();
            boolean ignoreForInactiveTenants = isIgnoreForInactiveTenants();
            for (Tenant tenant : tenants) {
                int tenantId = tenant.getId();
                if (ignoreForInactiveTenants && inactiveTenants.contains(tenantId)) {
                    log.info("Tenant " + tenant.getDomain() + " is inactive. Skipping claim data migration!");
                    continue;
                }
                try {
                    PrivilegedCarbonContext.startTenantFlow();
                    PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
                    carbonContext.setTenantId(tenant.getId());
                    carbonContext.setTenantDomain(tenant.getDomain());
                    IdentityTenantUtil.getTenantRegistryLoader().loadTenantRegistry(tenant.getId());

                    migrateTenantEmailTemplates(emailTemplates, tenant.getDomain());
                } catch (RegistryException e) {
                    if (!isContinueOnError()) {
                        throw e;
                    }
                    log.error("Error while migrating email templates for tenant : " + tenant.getDomain(), e);
                } finally {
                    PrivilegedCarbonContext.endTenantFlow();
                }
            }
        } catch (RegistryException e) {
            log.error("Error while migrating email templates", e);
        }
    }

    private void migrateTenantEmailTemplates(List<NotificationTemplate> emailTemplates, String tenantDomain)
            throws RegistryException {

        for (NotificationTemplate template : emailTemplates) {
            String displayName = template.getDisplayName();
            String normalizedDisplayName = getNormalizedName(displayName);
            String locale = template.getLocale();
            String path = Constant.EMAIL_TEMPLATE_PATH + PATH_SEPARATOR + normalizedDisplayName;
            if (!registryResourceMgtService.isResourceExists(path, tenantDomain)) {
                Collection collection = createTemplateType(normalizedDisplayName, displayName);
                registryResourceMgtService.putIdentityResource(collection, path, tenantDomain);
                Resource templateResource = createTemplateRegistryResource(template);
                registryResourceMgtService.putIdentityResource(templateResource, path, tenantDomain, locale);
            }
        }
    }

    public List<NotificationTemplate> loadEmailTemplates() {

        String filePath = Utility.getDataFilePath(EMAIL_ADMIN_CONFIG, getVersionConfig().getVersion());

        List<NotificationTemplate> defaultTemplates = new ArrayList<>();
        File configFile = new File(filePath);
        if (!configFile.exists()) {
            log.error("Email Configuration File is not present at: " + filePath);
        }

        XMLStreamReader xmlStreamReader = null;
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(configFile);
            xmlStreamReader = XMLInputFactory.newInstance().createXMLStreamReader(inputStream);
            StAXOMBuilder builder = new StAXOMBuilder(xmlStreamReader);

            OMElement documentElement = builder.getDocumentElement();
            Iterator iterator = documentElement.getChildElements();
            while (iterator.hasNext()) {
                OMElement omElement = (OMElement) iterator.next();
                String type = omElement.getAttributeValue(new QName(Constant.TEMPLATE_TYPE));
                String displayName = omElement.getAttributeValue(new QName(Constant.TEMPLATE_TYPE_DISPLAY_NAME));
                String locale = omElement.getAttributeValue(new QName(Constant.TEMPLATE_LOCALE));
                String contentType = omElement.getAttributeValue(new QName(Constant.TEMPLATE_CONTENT_TYPE));

                Map<String, String> emailContentMap = getEmailContent(omElement);
                String subject = emailContentMap.get(Constant.TEMPLATE_SUBJECT);
                String body = emailContentMap.get(Constant.TEMPLATE_BODY);
                String footer = emailContentMap.get(Constant.TEMPLATE_FOOTER);

                // create the DTO and add to list
                NotificationTemplate notificationTemplate = new NotificationTemplate();
                notificationTemplate.setType(type);
                notificationTemplate.setDisplayName(displayName);
                notificationTemplate.setLocale(locale);
                notificationTemplate.setContentType(contentType);

                notificationTemplate.setSubject(subject);
                notificationTemplate.setBody(body);
                notificationTemplate.setFooter(footer);

                defaultTemplates.add(notificationTemplate);
            }
        } catch (XMLStreamException | FileNotFoundException e) {
            log.warn("Error while loading email templates from " + filePath, e);
        } finally {
            try {
                if (xmlStreamReader != null) {
                    xmlStreamReader.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (XMLStreamException e) {
                log.error("Error while closing XML stream", e);
            } catch (IOException e) {
                log.error("Error while closing input stream", e);
            }
        }
        return defaultTemplates;
    }

    private Resource createTemplateRegistryResource(NotificationTemplate notificationTemplate)
            throws RegistryException {

        String displayName = notificationTemplate.getDisplayName();
        String type = getNormalizedName(displayName);
        String locale = notificationTemplate.getLocale();
        String body = notificationTemplate.getBody();

        // Set template properties.
        Resource templateResource = new ResourceImpl();
        templateResource.setProperty(Constant.TEMPLATE_TYPE_DISPLAY_NAME, displayName);
        templateResource.setProperty(Constant.TEMPLATE_TYPE, type);
        templateResource.setProperty(Constant.TEMPLATE_LOCALE, locale);

        String[] templateContent = new String[]{notificationTemplate.getSubject(), body,
                notificationTemplate.getFooter()};
        templateResource.setProperty(Constant.TEMPLATE_CONTENT_TYPE, notificationTemplate.getContentType());

        templateResource.setMediaType(RegistryConstants.TAG_MEDIA_TYPE);
        String content = new Gson().toJson(templateContent);
        byte[] contentByteArray = content.getBytes(StandardCharsets.UTF_8);
        templateResource.setContent(contentByteArray);
        return templateResource;
    }
}
