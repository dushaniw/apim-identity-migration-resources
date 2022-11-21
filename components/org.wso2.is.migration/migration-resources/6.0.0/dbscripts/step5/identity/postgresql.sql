CREATE OR REPLACE FUNCTION skip_index_if_exists(nameOfTheIndex varchar(64), tableName varchar(64), tableColumns varchar(64)) RETURNS void AS $$ declare s varchar(1000); declare result INTEGER; begin SELECT COUNT(1) into result FROM pg_indexes WHERE indexname = nameOfTheIndex; IF result = 0 THEN s :=  CONCAT('CREATE INDEX ' , nameOfTheIndex , ' ON ' , tableName, tableColumns); execute s; end if; END;$$ LANGUAGE plpgsql;

SELECT skip_index_if_exists('idx_tk_value_type','idn_oauth2_token_binding','(token_binding_value, token_binding_type)');

DROP FUNCTION skip_index_if_exists(varchar,varchar,varchar);

-- --------------------------- INDEX CREATION -----------------------------

-- IDN_OIDC_PROPERTY --

CREATE INDEX IDX_IOP_CK ON IDN_OIDC_PROPERTY(CONSUMER_KEY);

-- --------------------------- REMOVE UNUSED INDICES -----------------------------

-- IDN_OAUTH2_ACCESS_TOKEN --
DROP INDEX IDX_AT_CK_AU;

DROP INDEX IDX_AT_AU_TID_UD_TS_CKID;

DROP INDEX IDX_AT_AU_CKID_TS_UT;

-- IDN_OIDC_PROPERTY --
DROP INDEX IDX_IOP_TID_CK;

------------------------ ORGANIZATION MANAGEMENT TABLES -------------------------

DROP TABLE IF EXISTS SP_SHARED_APP;
DROP SEQUENCE IF EXISTS SP_SHARED_APP_PK_SEQ;
CREATE SEQUENCE SP_SHARED_APP_PK_SEQ;
CREATE TABLE SP_SHARED_APP(
    ID INTEGER DEFAULT NEXTVAL('SP_SHARED_APP_PK_SEQ'),
    MAIN_APP_ID CHAR(36) NOT NULL,
    OWNER_ORG_ID CHAR(36) NOT NULL,
    SHARED_APP_ID CHAR(36) NOT NULL,
    SHARED_ORG_ID CHAR(36) NOT NULL,
    PRIMARY KEY (ID),
    FOREIGN KEY (MAIN_APP_ID) REFERENCES SP_APP(UUID) ON DELETE CASCADE,
    FOREIGN KEY (SHARED_APP_ID) REFERENCES SP_APP(UUID) ON DELETE CASCADE,
    UNIQUE (MAIN_APP_ID, OWNER_ORG_ID, SHARED_ORG_ID),
    UNIQUE (SHARED_APP_ID)
);
