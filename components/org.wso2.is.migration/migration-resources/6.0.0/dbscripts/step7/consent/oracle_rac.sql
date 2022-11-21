CREATE OR REPLACE PROCEDURE ADD_PRIMARY_KEY_COLUMN(TABLE_NAME IN VARCHAR2, SEQUENCE_NAME IN VARCHAR2, TRIGGER_NAME IN VARCHAR2)
    IS
BEGIN
    DECLARE
        ADD_COLUMN_STATEMENT VARCHAR2(300);
        SEQ_STATEMENT VARCHAR2(400);
        TRIG_STATEMENT VARCHAR2(600);
        UPDATE_STATEMENT VARCHAR2(300);
        PK_STATEMENT VARCHAR2(300);

    BEGIN
        ADD_COLUMN_STATEMENT := 'ALTER TABLE ' || TABLE_NAME || ' ADD ID INTEGER DEFAULT 0 NOT NULL';
        SEQ_STATEMENT := 'CREATE SEQUENCE ' || SEQUENCE_NAME || ' START WITH 1 INCREMENT BY 1 NOCACHE';
        TRIG_STATEMENT := 'CREATE OR REPLACE TRIGGER ' || TRIGGER_NAME || ' BEFORE INSERT ON ' || TABLE_NAME || ' REFERENCING NEW AS NEW FOR EACH ROW BEGIN SELECT ' || SEQUENCE_NAME || '.nextval INTO :NEW.ID FROM dual; END;';
        UPDATE_STATEMENT := 'UPDATE ' || TABLE_NAME || ' SET ID = ' || SEQUENCE_NAME || '.NEXTVAL';
        PK_STATEMENT := 'ALTER TABLE ' || TABLE_NAME || ' ADD PRIMARY KEY (ID)';

        EXECUTE IMMEDIATE ADD_COLUMN_STATEMENT;
        EXECUTE IMMEDIATE SEQ_STATEMENT;
        EXECUTE IMMEDIATE TRIG_STATEMENT;
        EXECUTE IMMEDIATE UPDATE_STATEMENT;
        EXECUTE IMMEDIATE PK_STATEMENT;
        COMMIT;
    END;
END;
/

CALL ADD_PRIMARY_KEY_COLUMN('CM_SP_PURPOSE_PURPOSE_CAT_ASSC', 'CM_SP_PURPOSE_PPS_CT_ASSC_SEQ', 'CM_SP_PURPOSE_PPS_CT_ASSC_TRIG')
/

CALL ADD_PRIMARY_KEY_COLUMN('CM_PURPOSE_PII_CAT_ASSOC', 'CM_PURPOSE_PII_CAT_ASSOC_SEQ', 'CM_PURPOSE_PII_CAT_ASSOC_TRIG')
/

CALL ADD_PRIMARY_KEY_COLUMN('CM_SP_PURPOSE_PII_CAT_ASSOC', 'CM_SP_PURPOSE_PII_CT_ASSC_SEQ', 'CM_SP_PURPOSE_PII_CT_ASSC_TRIG')
/

CALL ADD_PRIMARY_KEY_COLUMN('CM_CONSENT_RECEIPT_PROPERTY', 'CM_CONSENT_RECEIPT_SEQ', 'CM_CONSENT_RECEIPT_TRIG')
/

DROP PROCEDURE ADD_PRIMARY_KEY_COLUMN
/
