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

public class SQLConstants {

    public static final String RETRIEVE_ALL_BPS_PROFILES =
            "SELECT PASSWORD, PROFILE_NAME, TENANT_ID FROM WF_BPS_PROFILE";

    public static final String UPDATE_BPS_PROFILE_PASSWORD =
            "UPDATE WF_BPS_PROFILE SET PASSWORD=? WHERE PROFILE_NAME=? AND TENANT_ID=?";

    public static final String RETRIEVE_ALL_TOTP_SECRET_KEY_CLAIM_DATA_WITH_MYSQL =
            "SELECT TENANT_ID, USER_NAME, DATA_VALUE, DATA_KEY FROM IDN_IDENTITY_USER_DATA WHERE " +
                    "DATA_KEY = ? OR DATA_KEY = ? LIMIT ? OFFSET ?";

    public static final String RETRIEVE_ALL_TOTP_SECRET_KEY_CLAIM_DATA_WITH_OTHER =
            "SELECT TENANT_ID, USER_NAME, DATA_VALUE, DATA_KEY FROM IDN_IDENTITY_USER_DATA WHERE " +
                    "DATA_KEY = ? OR DATA_KEY = ? ORDER BY USER_NAME OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

    public static final String UPDATE_TOTP_SECRET =
            "UPDATE IDN_IDENTITY_USER_DATA SET DATA_VALUE=? WHERE TENANT_ID=? AND USER_NAME=? AND DATA_KEY=?";

    public static final String RETRIEVE_PAGINATED_TOTP_SECRET_KEY_CLAIM_DATA_FROM_JDBC_USERSTORE_WITH_MYSQL =
            "SELECT UM_TENANT_ID, UM_USER_ID, UM_ATTR_VALUE, UM_ATTR_NAME FROM UM_USER_ATTRIBUTE WHERE " +
                    "UM_ATTR_NAME = ? OR UM_ATTR_NAME = ? LIMIT ? OFFSET ?";

    public static final String RETRIEVE_PAGINATED_TOTP_SECRET_KEY_CLAIM_DATA_FROM_JDBC_USERSTORE_WITH_OTHER =
            "SELECT UM_TENANT_ID, UM_USER_ID, UM_ATTR_VALUE, UM_ATTR_NAME FROM UM_USER_ATTRIBUTE WHERE " +
                    "UM_ATTR_NAME = ? OR UM_ATTR_NAME = ? ORDER BY UM_USER_ID OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

    public static final String UPDATE_TOTP_SECRET_TO_JDBC_USERSTORE =
            "UPDATE UM_USER_ATTRIBUTE SET UM_ATTR_VALUE=? WHERE " +
                    "UM_TENANT_ID=? AND UM_USER_ID=? AND UM_ATTR_NAME=?";

    public static final String RETRIEVE_PAGINATED_TOKENS_WITH_MYSQL =
            "SELECT ACCESS_TOKEN, REFRESH_TOKEN, TOKEN_ID " +
                    "FROM IDN_OAUTH2_ACCESS_TOKEN " +
                    "ORDER BY TOKEN_ID " +
                    "LIMIT ? OFFSET ?";

    public static final String RETRIEVE_PAGINATED_TOKENS_WITH_OTHER =
            "SELECT ACCESS_TOKEN, REFRESH_TOKEN, TOKEN_ID " +
                    "FROM IDN_OAUTH2_ACCESS_TOKEN " +
                    "ORDER BY TOKEN_ID " +
                    "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

    public static final String UPDATE_ENCRYPTED_ACCESS_TOKEN = "UPDATE IDN_OAUTH2_ACCESS_TOKEN SET " +
            "ACCESS_TOKEN=?, REFRESH_TOKEN=? WHERE TOKEN_ID=?";

    public static final String RETRIEVE_PAGINATED_AUTHORIZATION_CODES_MYSQL =
            "SELECT AUTHORIZATION_CODE, CODE_ID " +
                    "FROM IDN_OAUTH2_AUTHORIZATION_CODE " +
                    "ORDER BY CODE_ID " +
                    "LIMIT ? OFFSET ?";

    public static final String RETRIEVE_PAGINATED_AUTHORIZATION_CODES_OTHER =
            "SELECT AUTHORIZATION_CODE, CODE_ID " +
                    "FROM IDN_OAUTH2_AUTHORIZATION_CODE " +
                    "ORDER BY CODE_ID " +
                    "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

    public static final String UPDATE_ENCRYPTED_AUTHORIZATION_CODE =
            "UPDATE IDN_OAUTH2_AUTHORIZATION_CODE SET AUTHORIZATION_CODE=? WHERE CODE_ID=?";

    public static final String RETRIEVE_ALL_CONSUMER_SECRETS =
            "SELECT CONSUMER_SECRET, ID FROM IDN_OAUTH_CONSUMER_APPS";

    public static final String UPDATE_CONSUMER_SECRET =
            "UPDATE IDN_OAUTH_CONSUMER_APPS SET CONSUMER_SECRET=? WHERE ID=?";

    public static final String GET_WORKFLOW_REQUEST_QUERY = "SELECT UUID, REQUEST FROM WF_REQUEST";

    public static final String UPDATE_WORKFLOW_REQUEST_QUERY = "UPDATE WF_REQUEST SET REQUEST=? WHERE UUID=?";

}
