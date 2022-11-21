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

package org.wso2.carbon.is.migration.service.v5110.migrator;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.core.migrate.MigrationClientException;
import org.wso2.carbon.identity.scim2.common.exceptions.IdentitySCIMException;
import org.wso2.carbon.identity.scim2.common.group.SCIMGroupHandler;
import org.wso2.carbon.is.migration.internal.ISMigrationServiceDataHolder;
import org.wso2.carbon.is.migration.service.Migrator;
import org.wso2.carbon.is.migration.service.v5110.dao.RoleDAO;
import org.wso2.carbon.is.migration.util.Schema;
import org.wso2.carbon.is.migration.util.Utility;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.core.UserStoreException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.wso2.carbon.is.migration.util.Constant.SUPER_TENANT_ID;
import static org.wso2.carbon.user.core.UserCoreConstants.DOMAIN_SEPARATOR;
import static org.wso2.carbon.user.core.UserCoreConstants.INTERNAL_DOMAIN;

/**
 * Migrator for adding SCIM group data for Hybrid Roles.
 */
public class SCIMGroupRoleMigrator extends Migrator {

    private static final Logger log = LoggerFactory.getLogger(SCIMGroupRoleMigrator.class);

    @Override
    public void dryRun() throws MigrationClientException {

        log.info("Dry run capability not implemented in {} migrator.", this.getClass().getName());
    }

    @Override
    public void migrate() throws MigrationClientException {

        log.info("Starting to migrate SCIM group data for Roles.");
        migrateSuperTenantRoles();
        migrateTenantRoles();
        log.info("Successfully migrated SCIM group data for Roles.");
    }

    private void migrateSuperTenantRoles() throws MigrationClientException {

        try {
            if (log.isDebugEnabled()) {
                log.debug("Migrating SCIM group data of Roles of the Super Tenant.");
            }
            addSCIMGroupData(SUPER_TENANT_ID);
        } catch (org.wso2.carbon.user.api.UserStoreException e) {
            throw new MigrationClientException("Error while migrating Roles SCIM Group data.");
        }
    }

    private void migrateTenantRoles() throws MigrationClientException {

        try {
            Tenant[] tenantList = ISMigrationServiceDataHolder.getRealmService()
                    .getTenantManager().getAllTenants();

            for (Tenant tenant : tenantList) {
                try {
                    Utility.startTenantFlow(tenant);
                    if (log.isDebugEnabled()) {
                        log.debug("Migrating SCIM group data of Roles of the Tenant: " + tenant.getDomain());
                    }
                    addSCIMGroupData(tenant.getId());
                } finally {
                    PrivilegedCarbonContext.endTenantFlow();
                }
            }
        } catch (org.wso2.carbon.user.api.UserStoreException e) {
            throw new MigrationClientException("Error while migrating Roles SCIM Group data.");
        }
    }

    private List<String> getRolesListOfTenant(int tenantID) throws MigrationClientException {

        try (Connection connection = getDataSource(Schema.UM.getName()).getConnection()) {
            List<String> fullRoleNamesListOfTenant = RoleDAO.getInstance()
                    .getRoleNamesListOfTenant(connection, tenantID);
            List<String> filteredRolesWithDomain = new ArrayList<>();
            for (String roleName : fullRoleNamesListOfTenant) {
                if (StringUtils.equals(roleName, "everyone")) {
                    continue;
                }
                if (!roleName.contains(DOMAIN_SEPARATOR)) {
                    // Append "Internal" domain to roles without domains.
                    roleName = INTERNAL_DOMAIN + DOMAIN_SEPARATOR + roleName;
                }
                filteredRolesWithDomain.add(roleName);
            }
            return filteredRolesWithDomain;
        } catch (SQLException e) {
            throw new MigrationClientException("Error retrieving roles list of the tenant.", e);
        }
    }

    private void addSCIMGroupData(int tenantID)
            throws MigrationClientException, UserStoreException {

        SCIMGroupHandler scimGroupHandler = new SCIMGroupHandler(tenantID);
        List<String> rolesList = getRolesListOfTenant(tenantID);

        for (String role : rolesList) {
            try {
                if (!scimGroupHandler.isGroupExisting(role)) {
                    // If no attributes - i.e: group added via mgt console, not via SCIM endpoint.
                    // Add META.
                    scimGroupHandler.addMandatoryAttributes(role);
                }
            } catch (IdentitySCIMException e) {
                throw new UserStoreException("Error retrieving group information from SCIM Tables.", e);
            }
        }
    }
}
