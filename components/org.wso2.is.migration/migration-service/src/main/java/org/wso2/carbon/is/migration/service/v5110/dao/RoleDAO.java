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
package org.wso2.carbon.is.migration.service.v5110.dao;

import org.wso2.carbon.is.migration.service.v5110.bean.RoleInfo;
import org.wso2.carbon.user.core.UserCoreConstants;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RoleDAO implementation.
 */
public class RoleDAO {

    public static final String RETRIEVE_EXTERNAL_ROLE_DATA =
            "SELECT DISTINCT(UM_ROLE_PERMISSION.UM_ROLE_NAME), UM_ROLE_PERMISSION.UM_DOMAIN_ID, UM_DOMAIN"
                    + ".UM_DOMAIN_NAME, UM_ROLE_PERMISSION.UM_TENANT_ID FROM UM_ROLE_PERMISSION, UM_DOMAIN WHERE "
                    + " UM_ROLE_PERMISSION.UM_DOMAIN_ID = UM_DOMAIN.UM_DOMAIN_ID AND UM_ROLE_PERMISSION.UM_TENANT_ID ="
                    + " UM_DOMAIN.UM_TENANT_ID AND UM_ROLE_PERMISSION.UM_TENANT_ID = ?  AND UM_DOMAIN"
                    + ".UM_DOMAIN_NAME NOT IN (?, ?, ?, ?)";

    public static final String UPDATE_ROLE_NAME_SQL = "UPDATE UM_ROLE_PERMISSION SET UM_ROLE_NAME = ?, "
            + "UM_DOMAIN_ID = (SELECT UM_DOMAIN_ID FROM UM_DOMAIN WHERE UM_DOMAIN_NAME = ? AND "
            + "UM_TENANT_ID = ?) WHERE UM_ROLE_NAME = ? AND UM_TENANT_ID = ? AND "
            + "UM_DOMAIN_ID = ?";

    public static final String DELETE_ADMIN_GROUP_DATA =
            "DELETE FROM UM_ROLE_PERMISSION WHERE UM_ROLE_NAME= ? AND UM_TENANT_ID = ? AND "
                    + "UM_DOMAIN_ID NOT IN (SELECT UM_DOMAIN_ID FROM UM_DOMAIN WHERE UM_DOMAIN_NAME = "
                    + "? AND UM_TENANT_ID = ?)";

    public static final String DELETE_TENANT_ADMIN_ROLE_DATA =
            "DELETE FROM UM_ROLE_PERMISSION WHERE UM_ROLE_NAME = ? AND UM_TENANT_ID = ? AND "
                    + "UM_DOMAIN_ID IN (SELECT UM_DOMAIN_ID FROM UM_DOMAIN WHERE UM_DOMAIN_NAME = "
                    + "? AND UM_TENANT_ID = ?)";

    public static final String RETRIEVE_UM_DOMAIN_ID = "SELECT UM_DOMAIN_ID FROM UM_DOMAIN WHERE UM_DOMAIN_NAME=?";

    public static final String GET_ROLES_BY_TENANT = "SELECT UM_ROLE_NAME FROM UM_HYBRID_ROLE WHERE UM_TENANT_ID = ?";

    public static final String UM_ROLE_NAME = "UM_ROLE_NAME";
    public static final String UM_TENANT_ID = "UM_TENANT_ID";
    public static final String UM_DOMAIN_ID = "UM_DOMAIN_ID";
    public static final String UM_DOMAIN_NAME = "UM_DOMAIN_NAME";
    public static final String TENANT_ID = "TENANT_ID";

    private static RoleDAO instance = new RoleDAO();

    private RoleDAO() {

    }

    public static RoleDAO getInstance() {

        return instance;
    }

    /**
     * Method to retrieve external role data which has permissions assigned.
     *
     * @param connection Database connection.
     * @throws SQLException SQLException.
     */
    public List<RoleInfo> getExternalRoleData(Connection connection, int tenantID) throws SQLException {

        List<RoleInfo> externalRoles = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(RETRIEVE_EXTERNAL_ROLE_DATA)) {
            preparedStatement.setInt(1, tenantID);
            preparedStatement.setString(2, UserCoreConstants.APPLICATION_DOMAIN.toUpperCase(Locale.ENGLISH));
            preparedStatement.setString(3, UserCoreConstants.INTERNAL_DOMAIN.toUpperCase(Locale.ENGLISH));
            preparedStatement.setString(4, UserCoreConstants.SYSTEM_DOMAIN_NAME.toUpperCase(Locale.ENGLISH));
            preparedStatement.setString(5, UserCoreConstants.WORKFLOW_DOMAIN.toUpperCase(Locale.ENGLISH));
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    RoleInfo roleInfo = new RoleInfo();
                    roleInfo.setRoleName(resultSet.getString(UM_ROLE_NAME));
                    roleInfo.setDomainID(resultSet.getInt(UM_DOMAIN_ID));
                    roleInfo.setDomainName(resultSet.getString(UM_DOMAIN_NAME));
                    roleInfo.setTenantID(resultSet.getInt(UM_TENANT_ID));
                    externalRoles.add(roleInfo);
                }
            }
        }
        return externalRoles;
    }

    public int getDomainId(Connection connection, String domainName) throws SQLException {

        int domainId = -1;
        try (PreparedStatement preparedStatement = connection.prepareStatement(RETRIEVE_UM_DOMAIN_ID)) {
            preparedStatement.setString(1, domainName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    domainId = resultSet.getInt(UM_DOMAIN_ID);
                }
            }
        }
        return domainId;
    }

    /**
     * Method to transfer permissions to new role.
     *
     * @param connection Database connection.
     * @param roleInfo   Role details.
     * @throws SQLException SQLException.
     */
    public void transferPermissionsOfRole(Connection connection, RoleInfo roleInfo, boolean isAdminRole)
            throws SQLException {

        try (PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_ROLE_NAME_SQL)) {

            boolean autoCommitStatus = connection.getAutoCommit();
            connection.setAutoCommit(false);
            preparedStatement.setString(1, roleInfo.getInternalRoleName(isAdminRole));
            preparedStatement.setString(2, UserCoreConstants.INTERNAL_DOMAIN.toUpperCase(Locale.ENGLISH));
            preparedStatement.setInt(3, roleInfo.getTenantID());
            preparedStatement.setString(4, roleInfo.getRoleName());
            preparedStatement.setInt(5, roleInfo.getTenantID());
            preparedStatement.setInt(6, roleInfo.getDomainID());
            preparedStatement.executeUpdate();
            connection.commit();
            connection.setAutoCommit(autoCommitStatus);
        }
    }

    /**
     * Method to delete admin group permissions.
     *
     * @param connection     Database connection.
     * @param adminGroupName Admin group name.
     * @param tenantID       Tenant ID.
     * @return No of affected rows.
     * @throws SQLException SQLException.
     */
    public int deleteAdminGroupPermissions(Connection connection, String adminGroupName, int tenantID)
            throws SQLException {

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                DELETE_ADMIN_GROUP_DATA)) {

            boolean autoCommitStatus = connection.getAutoCommit();
            connection.setAutoCommit(false);
            preparedStatement.setString(1, adminGroupName);
            preparedStatement.setInt(2, tenantID);
            preparedStatement.setString(3, UserCoreConstants.INTERNAL_DOMAIN.toUpperCase(Locale.ENGLISH));
            preparedStatement.setInt(4, tenantID);
            int rows = preparedStatement.executeUpdate();
            connection.commit();
            connection.setAutoCommit(autoCommitStatus);
            return rows;
        }
    }

    /**
     * Method to delete tenant admin role permissions.
     *
     * @param connection     Database connection.
     * @param adminGroupName Admin group name.
     * @param tenantID       Tenant ID.
     * @throws SQLException SQLException.
     */
    public void deleteTenantAdminRolePermissions(Connection connection, String adminGroupName, int tenantID)
            throws SQLException {

        try (PreparedStatement preparedStatement = connection.prepareStatement(
                DELETE_TENANT_ADMIN_ROLE_DATA)) {

            boolean autoCommitStatus = connection.getAutoCommit();
            connection.setAutoCommit(false);
            preparedStatement.setString(1, adminGroupName);
            preparedStatement.setInt(2, tenantID);
            preparedStatement.setString(3, UserCoreConstants.INTERNAL_DOMAIN.toUpperCase(Locale.ENGLISH));
            preparedStatement.setInt(4, tenantID);
            preparedStatement.executeUpdate();
            connection.commit();
            connection.setAutoCommit(autoCommitStatus);
        }
    }

    /**
     * Get all roles of a particular tenant.
     *
     * @param connection    Database connection.
     * @param tenantID      Tenant ID.
     * @return              Roles list of tenant.
     * @throws SQLException Error when getting list of roles of tenant.
     */
    public List<String> getRoleNamesListOfTenant(Connection connection, int tenantID) throws SQLException {

        List<String> roleNamesList = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(GET_ROLES_BY_TENANT)) {
            preparedStatement.setInt(1, tenantID);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String roleName = resultSet.getString(UM_ROLE_NAME);
                    roleNamesList.add(roleName);
                }
                return roleNamesList;
            }
        }
    }
}
