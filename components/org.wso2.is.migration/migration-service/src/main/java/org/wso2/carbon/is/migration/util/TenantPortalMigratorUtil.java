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

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticatedUser;
import org.wso2.carbon.identity.application.common.IdentityApplicationManagementException;
import org.wso2.carbon.identity.application.common.model.Claim;
import org.wso2.carbon.identity.application.common.model.ClaimConfig;
import org.wso2.carbon.identity.application.common.model.ClaimMapping;
import org.wso2.carbon.identity.application.common.model.InboundAuthenticationConfig;
import org.wso2.carbon.identity.application.common.model.InboundAuthenticationRequestConfig;
import org.wso2.carbon.identity.application.common.model.LocalAndOutboundAuthenticationConfig;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.identity.application.common.model.ServiceProvider;
import org.wso2.carbon.identity.application.mgt.ApplicationManagementService;
import org.wso2.carbon.identity.application.mgt.ApplicationManagementServiceImpl;
import org.wso2.carbon.identity.application.mgt.ApplicationMgtSystemConfig;
import org.wso2.carbon.identity.application.mgt.dao.ApplicationDAO;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.oauth.IdentityOAuthAdminException;
import org.wso2.carbon.identity.oauth.OAuthUtil;
import org.wso2.carbon.identity.oauth.cache.AppInfoCache;
import org.wso2.carbon.identity.oauth.config.OAuthServerConfiguration;
import org.wso2.carbon.identity.oauth.dao.OAuthAppDAO;
import org.wso2.carbon.identity.oauth.dao.OAuthAppDO;
import org.wso2.carbon.identity.oauth.dto.OAuthConsumerAppDTO;
import org.wso2.carbon.is.migration.internal.ISMigrationServiceDataHolder;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.util.UserCoreUtil;
import org.wso2.carbon.utils.CarbonUtils;

import java.util.Arrays;
import java.util.List;

import static org.wso2.carbon.identity.oauth.common.OAuthConstants.GrantTypes.AUTHORIZATION_CODE;
import static org.wso2.carbon.identity.oauth.common.OAuthConstants.GrantTypes.REFRESH_TOKEN;
import static org.wso2.carbon.identity.oauth.common.OAuthConstants.OAuthVersions.VERSION_2;
import static org.wso2.carbon.utils.multitenancy.MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;
import static org.wso2.identity.apps.common.util.AppPortalConstants.DISPLAY_NAME_CLAIM_URI;
import static org.wso2.identity.apps.common.util.AppPortalConstants.EMAIL_CLAIM_URI;
import static org.wso2.identity.apps.common.util.AppPortalConstants.GRANT_TYPE_ACCOUNT_SWITCH;
import static org.wso2.identity.apps.common.util.AppPortalConstants.INBOUND_AUTH2_TYPE;
import static org.wso2.identity.apps.common.util.AppPortalConstants.TOKEN_BINDING_TYPE_COOKIE;

/**
 * This class has the utils related to TenantPortalMigrator.
 */
public class TenantPortalMigratorUtil {

    private static final Logger log = LoggerFactory.getLogger(TenantPortalMigratorUtil.class);

    private static final String ACTIVE_STATE = "ACTIVE";
    private static final String OAUTH_CONSUMER_SECRET_PROPERTY = "oauthConsumerSecret";

    /**
     * Create user portal application for existing tenants.
     *
     * @param tenantDomain  Tenant domain
     * @param tenantId      Tenant Id
     * @throws IdentityApplicationManagementException
     * @throws IdentityOAuthAdminException
     * @throws RegistryException
     * @throws UserStoreException
     */
    public static void initiatePortals(String tenantDomain, int tenantId)
            throws IdentityApplicationManagementException, IdentityOAuthAdminException, RegistryException,
            UserStoreException {

        ApplicationDAO applicationDAO = ApplicationMgtSystemConfig.getInstance().getApplicationDAO();

        UserRealm userRealm = ISMigrationServiceDataHolder.getRegistryService().getUserRealm(tenantId);
        String adminUsername = userRealm.getRealmConfiguration().getAdminUserName();

        for (AppPortal appPortal : AppPortal.values()) {
            if (appPortal.equals(AppPortal.ADMIN_PORTAL)) {
                String productVersion = CarbonUtils.getServerConfiguration().getFirstProperty("Version");
                // Skip admin portal creation for IS 5.10.0.
                if (StringUtils.isBlank(productVersion) || !productVersion.startsWith("5.11.0")) {
                    continue;
                }
            }
            if (applicationDAO.getApplication(appPortal.getName(), tenantDomain) == null) {
                // Initiate portal
                String consumerSecret = OAuthUtil.getRandomNumber();
                List<String> grantTypes = Arrays.asList(AUTHORIZATION_CODE, REFRESH_TOKEN, GRANT_TYPE_ACCOUNT_SWITCH);
                String consumerKey = appPortal.getConsumerKey();
                if (!SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                    consumerKey = consumerKey + "_" + tenantDomain;
                }
                createOAuth2Application(appPortal.getName(), appPortal.getPath(), consumerKey, consumerSecret,
                                adminUsername, tenantId, tenantDomain, TOKEN_BINDING_TYPE_COOKIE, grantTypes);
                createApplication(appPortal.getName(), adminUsername, appPortal.getDescription(),
                        consumerKey, consumerSecret, tenantDomain);
            }
        }
    }

    private static void createApplication(String appName, String appOwner, String appDescription, String consumerKey,
                                         String consumerSecret, String tenantDomain) throws IdentityApplicationManagementException {

        ServiceProvider serviceProvider = new ServiceProvider();
        serviceProvider.setApplicationName(appName);
        serviceProvider.setDescription(appDescription);

        InboundAuthenticationRequestConfig inboundAuthenticationRequestConfig
                = new InboundAuthenticationRequestConfig();
        inboundAuthenticationRequestConfig.setInboundAuthKey(consumerKey);
        inboundAuthenticationRequestConfig.setInboundAuthType(INBOUND_AUTH2_TYPE);
        Property property = new Property();
        property.setName(OAUTH_CONSUMER_SECRET_PROPERTY);
        property.setValue(consumerSecret);
        Property[] properties = { property };
        inboundAuthenticationRequestConfig.setProperties(properties);
        List<InboundAuthenticationRequestConfig> inboundAuthenticationRequestConfigs = Arrays
                .asList(inboundAuthenticationRequestConfig);
        InboundAuthenticationConfig inboundAuthenticationConfig = new InboundAuthenticationConfig();
        inboundAuthenticationConfig.setInboundAuthenticationRequestConfigs(
                inboundAuthenticationRequestConfigs.toArray(new InboundAuthenticationRequestConfig[0]));
        serviceProvider.setInboundAuthenticationConfig(inboundAuthenticationConfig);

        LocalAndOutboundAuthenticationConfig localAndOutboundAuthenticationConfig
                = new LocalAndOutboundAuthenticationConfig();
        localAndOutboundAuthenticationConfig.setUseUserstoreDomainInLocalSubjectIdentifier(true);
        localAndOutboundAuthenticationConfig.setUseTenantDomainInLocalSubjectIdentifier(true);
        localAndOutboundAuthenticationConfig.setSkipConsent(true);
        localAndOutboundAuthenticationConfig.setSkipLogoutConsent(true);
        serviceProvider.setLocalAndOutBoundAuthenticationConfig(localAndOutboundAuthenticationConfig);

        // Set requested claim mappings for the SP.
        ClaimConfig claimConfig = new ClaimConfig();
        claimConfig.setClaimMappings(getRequestedClaimMappings());
        claimConfig.setLocalClaimDialect(true);
        serviceProvider.setClaimConfig(claimConfig);

        ApplicationManagementService applicationManagementService = ApplicationManagementServiceImpl.getInstance();
        applicationManagementService.createApplication(serviceProvider, tenantDomain, appOwner);

        if (log.isDebugEnabled()) {
            log.debug(String.format("User portal application is created successfully for tenant %s.", tenantDomain));
        }
    }

    private static void createOAuth2Application(String applicationName, String portalPath, String consumerKey,
                                               String consumerSecret, String appOwner, int tenantId, String tenantDomain, String bindingType,
                                               List<String> grantTypes) throws IdentityOAuthAdminException {

        OAuthConsumerAppDTO oAuthConsumerAppDTO = new OAuthConsumerAppDTO();
        oAuthConsumerAppDTO.setApplicationName(applicationName);
        oAuthConsumerAppDTO.setOAuthVersion(VERSION_2);
        oAuthConsumerAppDTO.setOauthConsumerKey(consumerKey);
        oAuthConsumerAppDTO.setOauthConsumerSecret(consumerSecret);
        String callbackUrl = IdentityUtil.getServerURL(portalPath, true, true);
        if (!SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
            callbackUrl = callbackUrl.replace(portalPath, "/t/" + tenantDomain.trim() + portalPath);
        }
        oAuthConsumerAppDTO.setCallbackUrl(callbackUrl);
        oAuthConsumerAppDTO.setBypassClientCredentials(true);
        if (grantTypes != null && !grantTypes.isEmpty()) {
            oAuthConsumerAppDTO.setGrantTypes(String.join(" ", grantTypes));
        }
        oAuthConsumerAppDTO.setPkceMandatory(true);
        oAuthConsumerAppDTO.setTokenBindingType(bindingType);

        try {
            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext privilegedCarbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
            privilegedCarbonContext.setTenantId(tenantId);
            privilegedCarbonContext.setTenantDomain(tenantDomain);
            privilegedCarbonContext.setUsername(appOwner);
            registerAndRetrieveOAuthApplicationData(oAuthConsumerAppDTO);
            if (log.isDebugEnabled()) {
                log.debug(String.format("User portal is successfully registered as a OAuth 2 application for " +
                        "tenant %s.", tenantDomain));
            }
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }

    private static ClaimMapping[] getRequestedClaimMappings() {

        Claim emailClaim = new Claim();
        emailClaim.setClaimUri(EMAIL_CLAIM_URI);
        ClaimMapping emailClaimMapping = new ClaimMapping();
        emailClaimMapping.setRequested(true);
        emailClaimMapping.setLocalClaim(emailClaim);
        emailClaimMapping.setRemoteClaim(emailClaim);

        Claim roleClaim = new Claim();
        roleClaim.setClaimUri(DISPLAY_NAME_CLAIM_URI);
        ClaimMapping roleClaimMapping = new ClaimMapping();
        roleClaimMapping.setRequested(true);
        roleClaimMapping.setLocalClaim(roleClaim);
        roleClaimMapping.setRemoteClaim(roleClaim);

        return new ClaimMapping[] { emailClaimMapping, roleClaimMapping };
    }

    private static OAuthConsumerAppDTO registerAndRetrieveOAuthApplicationData(OAuthConsumerAppDTO application) throws
            IdentityOAuthAdminException {

        String tenantAwareLoggedInUser = CarbonContext.getThreadLocalCarbonContext().getUsername();
        OAuthAppDO app = new OAuthAppDO();
        String tenantDomain = CarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        OAuthAppDAO dao = new OAuthAppDAO();
        app.setApplicationName(application.getApplicationName());
        app.setCallbackUrl(application.getCallbackUrl());
        app.setState(ACTIVE_STATE);
        if (StringUtils.isEmpty(application.getOauthConsumerKey())) {
            app.setOauthConsumerKey(OAuthUtil.getRandomNumber());
            app.setOauthConsumerSecret(OAuthUtil.getRandomNumber());
        } else {
            app.setOauthConsumerKey(application.getOauthConsumerKey());
            if (StringUtils.isEmpty(application.getOauthConsumerSecret())) {
                app.setOauthConsumerSecret(OAuthUtil.getRandomNumber());
            } else {
                app.setOauthConsumerSecret(application.getOauthConsumerSecret());
            }
        }

        AuthenticatedUser appOwner = buildAuthenticatedUser(tenantAwareLoggedInUser, tenantDomain);
        app.setAppOwner(appOwner);
        if (application.getOAuthVersion() != null) {
            app.setOauthVersion(application.getOAuthVersion());
        } else {
            app.setOauthVersion(VERSION_2);
        }

        if (VERSION_2.equals(app.getOauthVersion())) {
            app.setGrantTypes(application.getGrantTypes());
            app.setScopeValidators(application.getScopeValidators());
            app.setAudiences(application.getAudiences());
            app.setPkceMandatory(application.getPkceMandatory());
            app.setPkceSupportPlain(application.getPkceSupportPlain());
            app.setUserAccessTokenExpiryTime(application.getUserAccessTokenExpiryTime());
            app.setApplicationAccessTokenExpiryTime(application.getApplicationAccessTokenExpiryTime());
            app.setRefreshTokenExpiryTime(application.getRefreshTokenExpiryTime());
            app.setIdTokenExpiryTime(application.getIdTokenExpiryTime());
            app.setRequestObjectSignatureValidationEnabled(application.isRequestObjectSignatureValidationEnabled());
            app.setIdTokenEncryptionEnabled(application.isIdTokenEncryptionEnabled());
            app.setIdTokenEncryptionAlgorithm(application.getIdTokenEncryptionAlgorithm());
            app.setIdTokenEncryptionMethod(application.getIdTokenEncryptionMethod());
            app.setBackChannelLogoutUrl(application.getBackChannelLogoutUrl());
            app.setFrontchannelLogoutUrl(application.getFrontchannelLogoutUrl());
            if (application.getTokenType() != null) {
                app.setTokenType(application.getTokenType());
            } else {
                app.setTokenType(getDefaultTokenType());
            }

            app.setBypassClientCredentials(application.isBypassClientCredentials());
            app.setRenewRefreshTokenEnabled(application.getRenewRefreshTokenEnabled());
            app.setTokenBindingType(application.getTokenBindingType());
        }

        dao.addOAuthApplication(app);
        AppInfoCache.getInstance().addToCache(app.getOauthConsumerKey(), app);
        return OAuthUtil.buildConsumerAppDTO(app);
    }

    private static String getDefaultTokenType() {

        return OAuthServerConfiguration.DEFAULT_TOKEN_TYPE;
    }

    private static AuthenticatedUser buildAuthenticatedUser(String tenantAwareUser, String tenantDomain) {

        AuthenticatedUser user = new AuthenticatedUser();
        user.setUserName(UserCoreUtil.removeDomainFromName(tenantAwareUser));
        user.setTenantDomain(tenantDomain);
        user.setUserStoreDomain(IdentityUtil.extractDomainFromName(tenantAwareUser));
        return user;
    }
}
