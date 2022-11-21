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

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.identity.claim.metadata.mgt.dao.CacheBackedExternalClaimDAO;
import org.wso2.carbon.identity.claim.metadata.mgt.dao.CacheBackedLocalClaimDAO;
import org.wso2.carbon.identity.claim.metadata.mgt.dao.ClaimDialectDAO;
import org.wso2.carbon.identity.claim.metadata.mgt.dao.ExternalClaimDAO;
import org.wso2.carbon.identity.claim.metadata.mgt.dao.LocalClaimDAO;
import org.wso2.carbon.identity.claim.metadata.mgt.exception.ClaimMetadataException;
import org.wso2.carbon.identity.claim.metadata.mgt.model.AttributeMapping;
import org.wso2.carbon.identity.claim.metadata.mgt.model.ClaimDialect;
import org.wso2.carbon.identity.claim.metadata.mgt.model.ExternalClaim;
import org.wso2.carbon.identity.claim.metadata.mgt.model.LocalClaim;
import org.wso2.carbon.identity.claim.metadata.mgt.util.ClaimConstants;
import org.wso2.carbon.identity.core.migrate.MigrationClientException;
import org.wso2.carbon.is.migration.internal.ISMigrationServiceDataHolder;
import org.wso2.carbon.is.migration.service.v540.util.FileBasedClaimBuilder;
import org.wso2.carbon.is.migration.util.Constant;
import org.wso2.carbon.is.migration.util.Utility;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.claim.ClaimMapping;
import org.wso2.carbon.user.core.claim.inmemory.ClaimConfig;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

/**
 * This class handles the claim data migration.
 */
public class ClaimDataMigrator extends Migrator {

    private static final Logger log = LoggerFactory.getLogger(ClaimDataMigrator.class);

    private ClaimConfig claimConfig;

    private ClaimDialectDAO claimDialectDAO = new ClaimDialectDAO();

    private CacheBackedLocalClaimDAO localClaimDAO = new CacheBackedLocalClaimDAO(new LocalClaimDAO());

    private CacheBackedExternalClaimDAO externalClaimDAO = new CacheBackedExternalClaimDAO(new ExternalClaimDAO());

    @Override
    public void dryRun() throws MigrationClientException {

        log.info("Dry run capability not implemented in {} migrator.", this.getClass().getName());
    }

    @Override
    public void migrate() throws MigrationClientException {

        String filePath = getDataFilePath();

        try {
            claimConfig = FileBasedClaimBuilder.buildClaimMappingsFromConfigFile(filePath);
        } catch (IOException | XMLStreamException | UserStoreException e) {
            String message = "Error while building claims from config file";
            if (isContinueOnError()) {
                log.error(message, e);
            } else {
                throw new MigrationClientException(message, e);
            }
        }

        if (claimConfig.getClaims().isEmpty()) {
            log.info(Constant.MIGRATION_LOG + "No data to migrate related with claim mappings.");
            return;
        }

        try {
            // Migrate super tenant.
            migrateClaimData(Constant.SUPER_TENANT_ID);

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
                    migrateClaimData(tenant.getId());
                } catch (UserStoreException | ClaimMetadataException e) {
                    String message = "Error while migrating claim data for tenant: " + tenant.getDomain();
                    if (isContinueOnError()) {
                        log.error(message, e);
                    } else {
                        throw e;
                    }
                }
            }
        } catch (UserStoreException | ClaimMetadataException e) {
            String message = "Error while migrating claim data";
            if (isContinueOnError()) {
                log.error(message, e);
            } else {
                throw new MigrationClientException(message, e);
            }
        }
    }

    /**
     * Migrate claim mappings.
     *
     * @param tenantId tenant id
     * @throws UserStoreException     UserStoreException
     * @throws ClaimMetadataException ClaimMetadataException
     */
    private void migrateClaimData(int tenantId) throws UserStoreException, ClaimMetadataException {

        UserRealm realm = ISMigrationServiceDataHolder.getRealmService().getTenantUserRealm(tenantId);
        if (realm == null) {
            return;
        }
        String primaryDomainName = realm.getRealmConfiguration().getUserStoreProperty(UserCoreConstants.RealmConfig
                .PROPERTY_DOMAIN_NAME);
        if (StringUtils.isBlank(primaryDomainName)) {
            primaryDomainName = UserCoreConstants.PRIMARY_DEFAULT_DOMAIN_NAME;
        }

        Set<String> claimDialects = new HashSet<>();
        for (ClaimDialect claimDialect : claimDialectDAO.getClaimDialects(tenantId)) {
            claimDialects.add(claimDialect.getClaimDialectURI());
        }

        Map<String, ClaimMapping> externalClaims = new HashMap<>();
        Set<String> existingLocalClaimURIs = new HashSet<>();

        // Add local claim mappings.
        for (Map.Entry<String, ClaimMapping> entry : claimConfig.getClaims()
                .entrySet()) {

            String claimURI = entry.getKey();
            ClaimMapping claimMapping = entry.getValue();
            String claimDialectURI = claimMapping.getClaim().getDialectURI();

            if (ClaimConstants.LOCAL_CLAIM_DIALECT_URI.equals(claimDialectURI)) {
                if (existingLocalClaimURIs.isEmpty()) {
                    existingLocalClaimURIs = getExistingLocalClaimURIs(tenantId);
                }

                if (existingLocalClaimURIs.contains(claimURI) && isOverrideExistingClaimEnabled()) {
                    log.info(Constant.MIGRATION_LOG + "Overriding local claim: " + claimURI + ", for the tenant: " +
                            tenantId);
                    updateLocalClaimMapping(tenantId, primaryDomainName, claimURI, claimMapping);
                } else {
                    if (existingLocalClaimURIs.contains(claimURI)) {
                        log.warn(Constant.MIGRATION_LOG + "Local claim: " + claimURI +
                                " already exists in the system for tenant: " + tenantId);
                        continue;
                    }
                    addLocalClaimMapping(tenantId, primaryDomainName, claimURI, claimMapping);
                    existingLocalClaimURIs.add(claimURI);
                }
            } else {
                externalClaims.put(entry.getKey(), entry.getValue());
            }
        }

        Map<String, Set<String>> existingExternalClaimURIs = new HashMap<>();
        // Add external claim mappings.
        for (Map.Entry<String, ClaimMapping> entry : externalClaims.entrySet()) {

            String claimURI = entry.getKey();
            String claimDialectURI = entry.getValue().getClaim().getDialectURI();
            if (!claimDialects.contains(claimDialectURI)) {
                claimDialectDAO.addClaimDialect(new ClaimDialect(claimDialectURI), tenantId);
                claimDialects.add(claimDialectURI);
                existingExternalClaimURIs.put(claimDialectURI, new HashSet<>());
            }

            if (existingExternalClaimURIs.get(claimDialectURI) == null) {
                existingExternalClaimURIs.put(claimDialectURI, getExistingExternalClaimURIs(tenantId, claimDialectURI));
            }

            if (existingExternalClaimURIs.get(claimDialectURI).contains(claimURI) && isOverrideExistingClaimEnabled()) {
                updateExternalClaimMapping(tenantId, claimURI, claimDialectURI);
            } else {
                if (existingExternalClaimURIs.get(claimDialectURI).contains(claimURI)) {
                    log.warn(Constant.MIGRATION_LOG + "External claim: " + claimURI +
                            " already exists in the system for dialect: " + claimDialectURI + " in tenant: " +
                            tenantId);
                    continue;
                }
                addExternalClaimMapping(tenantId, claimURI, claimDialectURI);
                existingExternalClaimURIs.get(claimDialectURI).add(claimURI);
            }
        }
    }

    /**
     * Get existing local claim URIs.
     *
     * @param tenantId tenant id
     * @return existing claim URI set
     * @throws ClaimMetadataException ClaimMetadataException
     */
    private Set<String> getExistingLocalClaimURIs(int tenantId) throws ClaimMetadataException {

        Set<String> localClaimURIs = new HashSet<>();
        for (LocalClaim localClaim : localClaimDAO.getLocalClaims(tenantId)) {
            localClaimURIs.add(localClaim.getClaimURI());
        }
        return localClaimURIs;
    }

    /**
     * Get existing external claim URIs.
     *
     * @param tenantId        tenant id
     * @param claimDialectURI claim dialect URI
     * @return existing external claim URIs
     * @throws ClaimMetadataException ClaimMetadataException
     */
    private Set<String> getExistingExternalClaimURIs(int tenantId, String claimDialectURI)
            throws ClaimMetadataException {

        Set<String> externalClaimURIs = new HashSet<>();
        for (ExternalClaim externalClaim : externalClaimDAO.getExternalClaims(claimDialectURI, tenantId)) {
            externalClaimURIs.add(externalClaim.getClaimURI());
        }
        return externalClaimURIs;
    }

    /**
     * Add local claim mapping.
     *
     * @param tenantId          tenant id
     * @param primaryDomainName primary domain name
     * @param claimURI          claim URI
     * @param claimMapping      claim mappings
     * @throws ClaimMetadataException ClaimMetadataException
     */
    private void addLocalClaimMapping(int tenantId, String primaryDomainName, String claimURI,
                                      ClaimMapping claimMapping) throws ClaimMetadataException {

        LocalClaim localClaim = getPreparedLocalClaim(primaryDomainName, claimURI, claimMapping);
        localClaimDAO.addLocalClaim(localClaim, tenantId);
    }

    private void updateLocalClaimMapping(int tenantId, String primaryDomainName, String claimURI,
                                      ClaimMapping claimMapping) throws ClaimMetadataException {

        LocalClaim localClaim = getPreparedLocalClaim(primaryDomainName, claimURI, claimMapping);
        localClaimDAO.updateLocalClaim(localClaim, tenantId);
    }

    private LocalClaim getPreparedLocalClaim(String primaryDomainName, String claimURI, ClaimMapping claimMapping) {

        List<AttributeMapping> mappedAttributes = new ArrayList<>();
        if (StringUtils.isNotBlank(claimMapping.getMappedAttribute())) {
            mappedAttributes.add(new AttributeMapping(primaryDomainName, claimMapping.getMappedAttribute()));
        }
        if (claimMapping.getMappedAttributes() != null) {
            for (Map.Entry<String, String> claimMappingEntry : claimMapping.getMappedAttributes().entrySet()) {
                mappedAttributes.add(new AttributeMapping(claimMappingEntry.getKey(), claimMappingEntry.getValue()));
            }
        }

        Map<String, String> claimProperties = claimConfig.getPropertyHolder().get(claimURI);
        claimProperties.remove(ClaimConstants.DIALECT_PROPERTY);
        claimProperties.remove(ClaimConstants.CLAIM_URI_PROPERTY);
        claimProperties.remove(ClaimConstants.ATTRIBUTE_ID_PROPERTY);

        if (!claimProperties.containsKey(ClaimConstants.DISPLAY_NAME_PROPERTY)) {
            claimProperties.put(ClaimConstants.DISPLAY_NAME_PROPERTY, "0");
        }
        if (claimProperties.containsKey(ClaimConstants.SUPPORTED_BY_DEFAULT_PROPERTY) &&
                StringUtils.isBlank(claimProperties.get(ClaimConstants.SUPPORTED_BY_DEFAULT_PROPERTY))) {
            claimProperties.put(ClaimConstants.SUPPORTED_BY_DEFAULT_PROPERTY, "true");
        }
        if (claimProperties.containsKey(ClaimConstants.READ_ONLY_PROPERTY) &&
                StringUtils.isBlank(claimProperties.get(ClaimConstants.READ_ONLY_PROPERTY))) {
            claimProperties.put(ClaimConstants.READ_ONLY_PROPERTY, "true");
        }
        if (claimProperties.containsKey(ClaimConstants.REQUIRED_PROPERTY) &&
                StringUtils.isBlank(claimProperties.get(ClaimConstants.REQUIRED_PROPERTY))) {
            claimProperties.put(ClaimConstants.REQUIRED_PROPERTY, "true");
        }

        return new LocalClaim(claimURI, mappedAttributes, claimProperties);
    }

    /**
     * Add external claim mapping.
     *
     * @param tenantId        tenant id
     * @param claimURI        claim URI
     * @param claimDialectURI claim dialect URI
     * @throws ClaimMetadataException ClaimMetadataException
     */
    private void addExternalClaimMapping(int tenantId, String claimURI, String claimDialectURI) throws
            ClaimMetadataException {

        ExternalClaim externalClaim = getPreparedExternalClaim(claimURI, claimDialectURI);
        externalClaimDAO.addExternalClaim(externalClaim, tenantId);
    }

    private void updateExternalClaimMapping(int tenantId, String claimURI, String claimDialectURI) throws
            ClaimMetadataException {

        ExternalClaim externalClaim = getPreparedExternalClaim(claimURI, claimDialectURI);
        externalClaimDAO.updateExternalClaim(externalClaim, tenantId);
    }

    private ExternalClaim getPreparedExternalClaim(String claimURI, String claimDialectURI) {

        String mappedLocalClaimURI = claimConfig.getPropertyHolder().get(claimURI)
                .get(ClaimConstants.MAPPED_LOCAL_CLAIM_PROPERTY);
        return new ExternalClaim(claimDialectURI, claimURI, mappedLocalClaimURI);
    }

    private String getDataFilePath() {

        boolean isUseOwnDataFile = false;
        if (getMigratorConfig() != null && getMigratorConfig().getParameters() != null) {
            isUseOwnDataFile = Boolean.parseBoolean(getMigratorConfig().getParameters().getProperty(Constant
                            .ClaimDataMigratorConstants.MIGRATOR_PARAMETER_USE_OWN_CLAIM_DATA_FILE));
        }

        return isUseOwnDataFile ?
                Utility.getDataFilePathWithFolderLocation(Paths.get(String.valueOf(getMigratorConfig().getOrder())),
                        Constant.CLAIM_CONFIG, getVersionConfig().getVersion()) :
                Utility.getDataFilePath(Constant.CLAIM_CONFIG, getVersionConfig().getVersion());
    }

    private boolean isOverrideExistingClaimEnabled() {

        if (getMigratorConfig() != null && getMigratorConfig().getParameters() != null) {
            return Boolean.parseBoolean(getMigratorConfig().getParameters().getProperty(Constant
                    .ClaimDataMigratorConstants.MIGRATOR_PARAMETER_OVERRIDE_EXISTING_CLAIMS));
        }
        return false;
    }
}
