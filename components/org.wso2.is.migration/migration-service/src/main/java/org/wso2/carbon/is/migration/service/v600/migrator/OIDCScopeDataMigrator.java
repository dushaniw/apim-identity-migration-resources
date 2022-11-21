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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.wso2.carbon.identity.claim.metadata.mgt.ClaimMetadataManagementServiceImpl;
import org.wso2.carbon.identity.claim.metadata.mgt.exception.ClaimMetadataException;
import org.wso2.carbon.identity.claim.metadata.mgt.model.ExternalClaim;
import org.wso2.carbon.identity.core.migrate.MigrationClientException;
import org.wso2.carbon.identity.oauth.dto.ScopeDTO;
import org.wso2.carbon.identity.oauth2.IdentityOAuth2Exception;
import org.wso2.carbon.identity.openidconnect.dao.ScopeClaimMappingDAOImpl;
import org.wso2.carbon.is.migration.service.Migrator;
import org.wso2.carbon.is.migration.util.Constant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.wso2.carbon.base.MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;
import static org.wso2.carbon.base.MultitenantConstants.SUPER_TENANT_ID;

/**
 * OIDCScopeDataMigrator for adding OIDC scope changes introduced in IS 6.0.0.
 */
public class OIDCScopeDataMigrator extends Migrator {

    private static final Logger log = LoggerFactory.getLogger(OIDCScopeDataMigrator.class);

    private static final String OIDC_CLAIM_DIALECT_URI = "http://wso2.org/oidc/claim";
    private static final String OIDC_USERNAME_CLAIM_URI = "username";
    private static final String OIDC_PROFILE_SCOPE_NAME = "profile";

    @Override
    public void migrate() throws MigrationClientException {

        migrateOIDCProfileScope();
    }

    @Override
    public void dryRun() throws MigrationClientException {

        log.info("Dry run capability not implemented in {} migrator.", this.getClass().getName());
    }

    private void migrateOIDCProfileScope() throws MigrationClientException {

        log.info(Constant.MIGRATION_LOG + "Started migrating OIDC profile scope in the super tenant.");

        try {
            ClaimMetadataManagementServiceImpl claimMetadataManagementService =
                    new ClaimMetadataManagementServiceImpl();
            List<ExternalClaim> oidcClaims = claimMetadataManagementService
                    .getExternalClaims(OIDC_CLAIM_DIALECT_URI, SUPER_TENANT_DOMAIN_NAME);
            ExternalClaim usernameClaim = oidcClaims.stream().filter(claim ->
                            OIDC_USERNAME_CLAIM_URI.equalsIgnoreCase(claim.getClaimURI()))
                    .findAny().orElse(null);
            if (usernameClaim == null) {
                throw new MigrationClientException("Username OIDC claim is not available.");
            }
            ScopeClaimMappingDAOImpl scopeClaimMappingDAO = new ScopeClaimMappingDAOImpl();
            ScopeDTO profileScopeDTO = scopeClaimMappingDAO.getScope(OIDC_PROFILE_SCOPE_NAME, SUPER_TENANT_ID);
            List<String> scopeClaimsList = new ArrayList<>(Arrays.asList(profileScopeDTO.getClaim()));
            if (!scopeClaimsList.contains(OIDC_USERNAME_CLAIM_URI)) {
                scopeClaimsList.add(OIDC_USERNAME_CLAIM_URI);
                profileScopeDTO.setClaim(scopeClaimsList.toArray(new String[0]));
                scopeClaimMappingDAO.updateScope(profileScopeDTO, SUPER_TENANT_ID);
            }
        } catch (IdentityOAuth2Exception | ClaimMetadataException | MigrationClientException e) {
            String message = Constant.MIGRATION_LOG + "Error while migrating oidc scopes";
            throw new MigrationClientException(message, e);
        }
        log.info(Constant.MIGRATION_LOG + "Completed OIDC profile scope migration in the super tenant.");
    }
}
