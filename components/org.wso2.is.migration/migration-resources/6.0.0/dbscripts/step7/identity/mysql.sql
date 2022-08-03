ALTER TABLE IDN_OAUTH2_ACCESS_TOKEN_AUDIT ADD ID INTEGER NOT NULL AUTO_INCREMENT, ADD PRIMARY KEY (ID);

ALTER TABLE IDN_OAUTH2_SCOPE_BINDING ADD ID INTEGER NOT NULL AUTO_INCREMENT, ADD PRIMARY KEY (ID);

ALTER TABLE IDN_AUTH_USER_SESSION_MAPPING ADD ID INTEGER NOT NULL AUTO_INCREMENT, ADD PRIMARY KEY (ID);

ALTER TABLE IDN_OAUTH2_CIBA_REQUEST_SCOPES ADD ID INTEGER NOT NULL AUTO_INCREMENT, ADD PRIMARY KEY (ID);

ALTER TABLE IDN_OAUTH2_ACCESS_TOKEN ADD CONSENTED_TOKEN VARCHAR(6);

ALTER TABLE IDN_FED_AUTH_SESSION_MAPPING DROP PRIMARY KEY;
ALTER TABLE IDN_FED_AUTH_SESSION_MAPPING ADD ID INTEGER NOT NULL AUTO_INCREMENT, ADD PRIMARY KEY (ID);
ALTER TABLE IDN_FED_AUTH_SESSION_MAPPING ADD TENANT_ID INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE IDN_FED_AUTH_SESSION_MAPPING ADD UNIQUE(IDP_SESSION_ID, TENANT_ID);
