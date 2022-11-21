
CREATE OR REPLACE FUNCTION USERID_MIGRATION_SP() RETURNS VOID
AS $$
DECLARE 
    chunkSize INT := 1000;
    threshold INT := 9999999;
    totalMigrated INT := 0;
    chunkCount INT := 1;
    query varchar(200);
    offset INT := 0;
    forceUpdate BOOLEAN := FALSE;
    scimEnable BOOLEAN := TRUE;
BEGIN
    
    EXECUTE 'CREATE TABLE TMP_UM_USER_UUID AS (SELECT UM_USER_NAME,UM_ID, UM_USER_ID FROM UM_USER)';

    WHILE (chunkCount!=0 and totalMigrated < threshold ) LOOP

        offset:=totalMigrated+chunkSize;

        CREATE TABLE TMP_UM_USER_UUID_CHUNK as SELECT * FROM TMP_UM_USER_UUID offset totalMigrated LIMIT chunkSize;
            
        execute 'ALTER TABLE TMP_UM_USER_UUID_CHUNK
        ADD CONSTRAINT pk PRIMARY KEY (UM_ID)';

        SELECT COUNT(*) FROM TMP_UM_USER_UUID_CHUNK INTO chunkCount;
        -- If it is force update then update the existing scim id with newly generated um_user_id in um_user table
        IF forceUpdate THEN
            execute 'UPDATE UM_USER_ATTRIBUTE as t1
            SET UM_ATTR_VALUE = t2.UM_USER_ID
            FROM TMP_UM_USER_UUID_CHUNK as t2 
            WHERE t1.UM_USER_ID = t2.UM_ID
            AND t1.UM_ATTR_VALUE != t2.UM_USER_ID
            and t1.UM_ATTR_NAME=''scimId''';
        END IF;

        -- insert scim id claim for the users who doesnt have one
        execute 'INSERT INTO UM_USER_ATTRIBUTE (UM_USER_ID, UM_ATTR_NAME, UM_ATTR_VALUE, UM_PROFILE_ID, UM_TENANT_ID)
        SELECT t1.UM_ID, ''scimId'', t1.UM_USER_ID, ''default'', -1234
        FROM TMP_UM_USER_UUID_CHUNK as t1
        LEFT JOIN 
        (SELECT DISTINCT(t8.UM_ID), t8.UM_USER_NAME
        FROM TMP_UM_USER_UUID_CHUNK as t8
        LEFT JOIN UM_USER_ATTRIBUTE as t9
        ON t8.UM_ID = t9.UM_USER_ID
        WHERE t9.UM_ATTR_NAME=''scimId'') as t2
        ON t1.UM_ID = t2.UM_ID
        WHERE t2.UM_ID IS NULL';

        -- insert  uid claim for the users who doesnt have one
        execute 'INSERT INTO UM_USER_ATTRIBUTE (UM_USER_ID, UM_ATTR_NAME, UM_ATTR_VALUE, UM_PROFILE_ID, UM_TENANT_ID)
        SELECT t1.UM_ID, ''uid'', t1.UM_USER_NAME, ''default'', -1234
        FROM TMP_UM_USER_UUID_CHUNK as t1
        LEFT JOIN 
        (SELECT DISTINCT(t8.UM_ID), t8.UM_USER_NAME
        FROM TMP_UM_USER_UUID_CHUNK as t8
        LEFT JOIN UM_USER_ATTRIBUTE as t9
        ON t8.UM_ID = t9.UM_USER_ID
        WHERE t9.UM_ATTR_NAME=''uid'') as t2
        ON t1.UM_ID = t2.UM_ID
        WHERE t2.UM_ID IS NULL';

        -- if scim enable and not a force update then generated um_user_id wll be replaced with user's existing scim claim value
        IF (NOT forceUpdate AND scimEnable) THEN

            CREATE TABLE TMP_UM_USER_SCIM AS
            (SELECT t1.UM_ID, t1.UM_USER_ID,t2.UM_ATTR_VALUE FROM TMP_UM_USER_UUID_CHUNK t1 
            INNER JOIN UM_USER_ATTRIBUTE t2 ON t1.UM_ID = t2.UM_USER_ID WHERE t2.UM_ATTR_NAME='scimId');

            ALTER TABLE TMP_UM_USER_SCIM
            ADD PRIMARY KEY (UM_ID),
            ADD UNIQUE(UM_ATTR_VALUE);

            UPDATE UM_USER as t1
            set um_user_id = t2.UM_ATTR_VALUE
            from TMP_UM_USER_SCIM as t2
            where t1.UM_ID = t2.UM_ID;

            drop table TMP_UM_USER_SCIM;

        END IF;
        totalMigrated := totalMigrated + chunkCount;
        DROP TABLE TMP_UM_USER_UUID_CHUNK;

    END LOOP;

    DROP TABLE TMP_UM_USER_UUID;

END;

$$ LANGUAGE plpgsql;

