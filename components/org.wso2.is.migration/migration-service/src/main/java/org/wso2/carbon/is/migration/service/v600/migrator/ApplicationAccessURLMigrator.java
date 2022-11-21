/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.com).
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

package org.wso2.carbon.is.migration.service.v600.migrator;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.identity.application.common.IdentityApplicationManagementException;
import org.wso2.carbon.identity.application.common.model.InboundAuthenticationConfig;
import org.wso2.carbon.identity.application.common.model.InboundAuthenticationRequestConfig;
import org.wso2.carbon.identity.application.common.model.ServiceProvider;
import org.wso2.carbon.identity.application.mgt.ApplicationManagementService;
import org.wso2.carbon.identity.base.IdentityException;
import org.wso2.carbon.identity.core.migrate.MigrationClientException;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.is.migration.service.Migrator;
import org.wso2.carbon.is.migration.service.v600.dao.ApplicationDAO;
import org.wso2.carbon.is.migration.util.Constant;
import org.wso2.carbon.is.migration.util.ReportUtil;
import org.wso2.carbon.is.migration.util.Schema;
import org.wso2.carbon.is.migration.util.Utility;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.user.api.Tenant;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.wso2.carbon.identity.application.authentication.framework.util.FrameworkConstants.StandardInboundProtocols.OAUTH2;
import static org.wso2.carbon.identity.application.authentication.framework.util.FrameworkConstants.StandardInboundProtocols.SAML2;
import static org.wso2.carbon.identity.application.common.util.IdentityApplicationConstants.DEFAULT_SP_CONFIG;
import static org.wso2.carbon.is.migration.util.Constant.REPORT_PATH;

public class ApplicationAccessURLMigrator extends Migrator {

    private static final Logger log = LoggerFactory.getLogger(ApplicationAccessURLMigrator.class);
    private static final String SP_REDIRECT_URL_RESOURCE_PATH = "/identity/config/relyingPartyRedirectUrls";
    private ReportUtil reportUtil;

    @Override
    public void dryRun() throws MigrationClientException {

        log.info(Constant.MIGRATION_LOG + "Executing dry run for {}", this.getClass().getName());
        Properties migrationProperties = getMigratorConfig().getParameters();
        String reportPath = (String) migrationProperties.get(REPORT_PATH);

        try {
            reportUtil = new ReportUtil(reportPath);
            reportUtil.writeMessage("\n--- Summary of the report - Relying party Urls Migration ---\n");
            reportUtil.writeMessage(
                    String.format("%40s | %40s | %40s | %40s", "Application ", "RelyingParty", "RedirectURL",
                            "Tenant Domain"));

            log.info(Constant.MIGRATION_LOG + "Started the dry run of Relying party Urls migration.");
            // Migrate super tenant
            migratingRelyingPartyURL(reportPath, MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.toString(), true);

            // Migrate other tenants
            Set<Tenant> tenants = Utility.getTenants();
            for (Tenant tenant : tenants) {
                if (isIgnoreForInactiveTenants() && !tenant.isActive()) {
                    log.info(Constant.MIGRATION_LOG + "Tenant " + tenant.getDomain() + " is inactive. Redirect " +
                            "URLs migration will be skipped. ");
                } else {
                    migratingRelyingPartyURL(reportPath, tenant.getDomain(), true);
                }
            }

            reportUtil.commit();
        } catch (IOException e) {
            log.error(Constant.MIGRATION_LOG + "Error while constructing the DryRun report.", e);
        }


    }

    private void migratingRelyingPartyURL(String reportPath, String tenantDomain, boolean isDryRun) throws MigrationClientException {

        List<ServiceProvider> applications = new ArrayList();
        Map<String, String> applicationAccessURLs = new HashMap<>();
        log.info("............................................................................................");
        if (isDryRun) {
            log.info(Constant.MIGRATION_LOG + "Started dry run of migrating redirect URLs for tenant: " + tenantDomain);
        } else {
            log.info(Constant.MIGRATION_LOG + "Started migrating redirect URLs for tenant: " + tenantDomain);
        }

        Properties relyingPartyDetails = getRelyingPartyRedirectUrlValues(tenantDomain);
        if (relyingPartyDetails == null) {
            log.info(Constant.MIGRATION_LOG + "There are no relying party redirect URLs configured for the tenant: "
                    + tenantDomain);
            return;
        }

        for (Object key : relyingPartyDetails.keySet()) {
            ServiceProvider sp;
            String relyingParty = key.toString();
            ArrayList relyingPartyPropValue = ((ArrayList) relyingPartyDetails.get(relyingParty));
            if (StringUtils.isNotEmpty(relyingParty) && CollectionUtils.isNotEmpty(relyingPartyPropValue)) {

                if (relyingPartyPropValue.get(0) == null) {
                    continue;
                }
                String redirectUrl = relyingPartyPropValue.get(0).toString();
                if (StringUtils.isEmpty(redirectUrl)) continue;

                // Retrieve an application of which oauth2 is configured as the inbound auth config.
                sp = getServiceProviderByRelyingParty(relyingParty, tenantDomain, OAUTH2);
                if (sp == null) {
                    // Retrieve an application of which saml2 is configured as the inbound auth config.
                    sp = getServiceProviderByRelyingParty(relyingParty, tenantDomain, SAML2);
                }
                if (sp != null) {
                    String applicationName = sp.getApplicationName();
                    if (StringUtils.isEmpty(sp.getAccessUrl())) {
                        applications.add(sp);
                        if (isDryRun) {
                            reportUtil.writeMessage(String.format("%40s | %40s | %40s | %40s ", applicationName,
                                    relyingParty, redirectUrl, tenantDomain));
                        } else {
                            applicationAccessURLs.put(applicationName, redirectUrl);
                            migrateRedirectURLFromRegistryToApplication(relyingParty, tenantDomain, sp, redirectUrl);
                        }
                    } else if (!redirectUrl.equals(sp.getAccessUrl())) {
                        applications.add(sp);
                        String message = String.format("Conflicting relying-party redirect URL: %s, found for the " +
                                "application: %s, where the access URL is already set to: %s by default or by" +
                                " another relying-party configuration. Please resolve the conflict and re-run" +
                                " the migration.", redirectUrl, applicationName, sp.getAccessUrl());
                        log.error(message);
                        if (isDryRun) {
                            reportUtil.writeMessage(String.format("%40s | %40s | %40s | %40s ", applicationName,
                                    relyingParty, redirectUrl, tenantDomain));
                        } else {
                            throw new MigrationClientException(message);
                        }
                    }
                }
            }
        }

        if (CollectionUtils.isNotEmpty(applications)) {
            applications.stream().collect(Collectors.groupingBy(Function.identity(),
                    Collectors.counting()))
                    .entrySet().stream()
                    .filter(e -> e.getValue() > 1L)
                    .map(e -> e.getKey())
                    .collect(Collectors.toList())
                    .forEach(sp -> reportIssues(sp, reportPath, isDryRun));
        }
        if (!isDryRun) {
            removeRelyingPartyRedirectUrlsFRomRegistry(tenantDomain);
        }
    }

    private void reportIssues(ServiceProvider sp, String reportPath, boolean isDryRun) {

        if (isDryRun) {
            String message = "There are multiple relyingParty values defined for the application: " +
                    sp.getApplicationName() + " Refer the report at " + reportPath + " to get more details and find " +
                    "duplicates and resolve the issues by deleting duplicates from the config registry at path: " +
                    SP_REDIRECT_URL_RESOURCE_PATH;
            log.error(Constant.MIGRATION_LOG + message);
        } else {
            String message = "There were multiple relyingParty values defined for the application: " +
                    sp.getApplicationName() + ". As the application access URL is set to the last occurrence. Please "
                    + "manually verify the access url of this application.";
            log.warn(Constant.MIGRATION_LOG + message);
        }
    }

    @Override
    public void migrate() throws MigrationClientException {

        // Migrate super tenant
        migratingRelyingPartyURL(null, MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.toString(), false);

        Set<Tenant> tenants = Utility.getTenants();
        for (Tenant tenant : tenants) {
            String tenantDomain = tenant.getDomain();
            log.info(Constant.MIGRATION_LOG + "Started to migrate redirect URLs for tenant: " + tenantDomain);
            if (isIgnoreForInactiveTenants() && !tenant.isActive()) {
                log.info(Constant.MIGRATION_LOG + "Tenant " + tenant.getDomain() + " is inactive. Skipping redirect " +
                        "URLs migration. ");
                continue;
            } else {
                migratingRelyingPartyURL(null, tenant.getDomain(), false);
            }
        }

    }

    /**
     * Returns Properties which contains the redirect url configured in the registry against relying party.
     *
     * @param tenantDomain Tenant Domain.
     * @return Redirect URL.
     */
    private static Properties getRelyingPartyRedirectUrlValues(String tenantDomain) {

        if (log.isDebugEnabled()) {
            log.debug("Retrieving configured url against relying parties for tenant domain : " +
                    tenantDomain);
        }

        int tenantId;
        if (StringUtils.isEmpty(tenantDomain)) {
            if (log.isDebugEnabled()) {
                log.debug("Tenant domain is not available. Hence using super tenant domain");
            }
            tenantDomain = MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;
            tenantId = MultitenantConstants.SUPER_TENANT_ID;
        } else {
            tenantId = IdentityTenantUtil.getTenantId(tenantDomain);
        }
        try {
            IdentityTenantUtil.initializeRegistry(tenantId, tenantDomain);
            Registry registry = IdentityTenantUtil.getConfigRegistry(tenantId);
            if (registry.resourceExists(SP_REDIRECT_URL_RESOURCE_PATH)) {
                Resource resource = registry.get(SP_REDIRECT_URL_RESOURCE_PATH);
                if (resource != null) {
                    return resource.getProperties();
                }
            }
        } catch (RegistryException e) {
            log.error(Constant.MIGRATION_LOG + "Error while getting data from the registry.", e);
        } catch (IdentityException e) {
            log.error(Constant.MIGRATION_LOG + "Error while initializing the registry for : " + tenantDomain, e);
        }
        return null;
    }

    /**
     * Returns Properties which contains the redirect url configured in the registry against relying party.
     *
     * @param tenantDomain Tenant Domain.
     * @return Redirect URL.
     */
    private static void removeRelyingPartyRedirectUrlsFRomRegistry(String tenantDomain) {

        if (log.isDebugEnabled()) {
            log.debug("Removing configured redirect url against relying parties for tenant domain : " +
                    tenantDomain);
        }

        int tenantId;
        if (StringUtils.isEmpty(tenantDomain)) {
            if (log.isDebugEnabled()) {
                log.debug("Tenant domain is not available. Hence using super tenant domain");
            }
            tenantDomain = MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;
            tenantId = MultitenantConstants.SUPER_TENANT_ID;
        } else {
            tenantId = IdentityTenantUtil.getTenantId(tenantDomain);
        }
        try {
            IdentityTenantUtil.initializeRegistry(tenantId, tenantDomain);
            Registry registry = IdentityTenantUtil.getConfigRegistry(tenantId);
            if (registry.resourceExists(SP_REDIRECT_URL_RESOURCE_PATH)) {
                registry.delete(SP_REDIRECT_URL_RESOURCE_PATH);
            }
        } catch (RegistryException e) {
            log.error(Constant.MIGRATION_LOG + "Error while removing data from the registry.", e);
        } catch (IdentityException e) {
            log.error(Constant.MIGRATION_LOG + "Error while initializing the registry for : " + tenantDomain, e);
        }
    }

    /**
     * If the relying party is a valid inbound authenticator configured in the application, then update the
     * applications access URL with the redirect URL defined in the registry (SP_REDIRECT_URL_RESOURCE_PATH) for
     * relying party
     *
     * @param relyingParty
     * @param tenantDomain
     * @param sp
     * @param redirectUrl
     */
    private void migrateRedirectURLFromRegistryToApplication(String relyingParty, String tenantDomain,
                                                             ServiceProvider sp, String redirectUrl) {

        InboundAuthenticationConfig inboundAuthenticationConfig = sp.getInboundAuthenticationConfig();
        InboundAuthenticationRequestConfig[] inboundAuthenticationRequestConfig = inboundAuthenticationConfig
                .getInboundAuthenticationRequestConfigs();
        for (InboundAuthenticationRequestConfig inboundAuth : inboundAuthenticationRequestConfig) {
            if (relyingParty.equals(inboundAuth.getInboundAuthKey())) {
                log.info("Updating the application: " + sp.getApplicationName() + " access URL with redirect URL: " +
                        redirectUrl + " configured for relyingParty: " + relyingParty);
                sp.setAccessUrl(redirectUrl);
                try (Connection connection = getDataSource(Schema.IDENTITY.getName()).getConnection()) {
                    new ApplicationDAO().updateAccessURL(connection, sp.getApplicationName(),
                            redirectUrl, IdentityTenantUtil.getTenantId(tenantDomain));
                } catch (SQLException e) {
                    log.error(Constant.MIGRATION_LOG + "Unable to update the application: "
                            + sp.getApplicationName() + " with accessURL:" + relyingParty, e);
                } catch (MigrationClientException e) {
                    log.error(Constant.MIGRATION_LOG + "Unable to update the application: "
                            + sp.getApplicationName() + " with accessURL:" + relyingParty, e);
                }
                break;
            }
        }
    }

    private static ServiceProvider getServiceProviderByRelyingParty(String relyingParty, String tenantDomain, String
            type) {

        ServiceProvider sp = null;
        try {
            sp = ApplicationManagementService.getInstance().getServiceProviderByClientId(relyingParty,
                    type, tenantDomain);
            if (sp != null && DEFAULT_SP_CONFIG.equals(sp.getApplicationName())) {
                return null;
            }
        } catch (IdentityApplicationManagementException e) {
            log.warn("Unable to retrieve an application for the relying party: " + relyingParty + " of type: " +
                    type + " in the tenant: " + tenantDomain);
        }
        return sp;
    }
}
