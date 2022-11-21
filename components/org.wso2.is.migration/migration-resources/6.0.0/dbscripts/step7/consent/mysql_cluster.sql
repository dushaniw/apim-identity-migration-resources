DROP PROCEDURE IF EXISTS add_or_alter_pk_column;

DELIMITER $$
CREATE PROCEDURE add_or_alter_pk_column(db_table_name VARCHAR(100))
BEGIN
    IF EXISTS(SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=db_table_name) THEN
        IF EXISTS(SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=db_table_name AND COLUMN_KEY='PRI') THEN
            BEGIN
                SELECT COLUMN_NAME INTO @pk_column_name FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=db_table_name AND COLUMN_KEY='PRI';
                SET @s = CONCAT('ALTER TABLE ', db_table_name, ' RENAME COLUMN ', @pk_column_name, ' TO ID');
            END;
        ELSE
            BEGIN
                SET @s = CONCAT('ALTER TABLE ', db_table_name, ' ADD ID INTEGER NOT NULL AUTO_INCREMENT, ADD PRIMARY KEY (ID)');
            END;
        END IF;
       	PREPARE stmt FROM @s;
        EXECUTE stmt;
    END IF;
END $$
DELIMITER ;

CALL add_or_alter_pk_column('CM_SP_PURPOSE_PURPOSE_CAT_ASSC');
CALL add_or_alter_pk_column('CM_PURPOSE_PII_CAT_ASSOC');
CALL add_or_alter_pk_column('CM_SP_PURPOSE_PII_CAT_ASSOC');
CALL add_or_alter_pk_column('CM_CONSENT_RECEIPT_PROPERTY');

DROP PROCEDURE IF EXISTS add_or_alter_pk_column;
