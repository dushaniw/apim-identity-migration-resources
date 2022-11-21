DROP PROCEDURE IF EXISTS USERID_MIGRATION_SP;

DELIMITER //

CREATE PROCEDURE USERID_MIGRATION_SP()
BEGIN 
    DECLARE chunkSize INT DEFAULT 1000;
    DECLARE threshold INT DEFAULT 9999999;
    DECLARE totalMigrated INT DEFAULT 0;
    DECLARE chunkCount INT DEFAULT 1;
    DECLARE query varchar(200);
    DECLARE offset INT DEFAULT 0;
    DECLARE forceUpdate BOOLEAN  DEFAULT  FALSE;
    DECLARE scimEnable BOOLEAN  DEFAULT  TRUE;
    
	BEGIN
      	CREATE TABLE TMP_UM_USER_UUID AS (SELECT UM_USER_NAME,UM_ID, UM_USER_ID FROM UM_USER);
     
        WHILE (chunkCount!=0 and totalMigrated < threshold ) DO
            
            SET offset=totalMigrated+chunkSize;
                
            CREATE TABLE TMP_UM_USER_UUID_CHUNK SELECT * FROM TMP_UM_USER_UUID LIMIT totalMigrated, chunkSize;
                                    
            ALTER TABLE TMP_UM_USER_UUID_CHUNK
            ADD CONSTRAINT pk PRIMARY KEY (UM_ID);
       	    
            SELECT COUNT(*) FROM TMP_UM_USER_UUID_CHUNK INTO chunkCount;
            
            -- If it is force update then update the existing scim id with newly generated um_user_id in um_user table
            IF forceUpdate THEN

                UPDATE UM_USER_ATTRIBUTE t1
                INNER JOIN TMP_UM_USER_UUID_CHUNK t2 
                ON t1.UM_USER_ID = t2.UM_ID
                SET t1.UM_ATTR_VALUE = t2.UM_USER_ID
                WHERE t1.UM_ATTR_VALUE != t2.UM_USER_ID
                and t1.UM_ATTR_NAME='scimId';
			END IF;
             -- insert scimid claim for the users who doesnt have one
            INSERT INTO UM_USER_ATTRIBUTE (UM_USER_ID, UM_ATTR_NAME, UM_ATTR_VALUE, UM_PROFILE_ID, UM_TENANT_ID)
            SELECT t1.UM_ID, 'scimId', t1.UM_USER_ID, 'default', -1234
                FROM TMP_UM_USER_UUID_CHUNK t1
                LEFT JOIN 
                    (SELECT DISTINCT(t8.UM_ID), t8.UM_USER_NAME
                    FROM TMP_UM_USER_UUID_CHUNK t8
                    LEFT JOIN UM_USER_ATTRIBUTE t9
                    ON t8.UM_ID = t9.UM_USER_ID
                    WHERE t9.UM_ATTR_NAME='scimId') t2
                ON t1.UM_ID = t2.UM_ID
                WHERE t2.UM_ID IS NULL;
           
		  -- insert scim id claim for the users who doesnt have one
            INSERT INTO UM_USER_ATTRIBUTE (UM_USER_ID, UM_ATTR_NAME, UM_ATTR_VALUE, UM_PROFILE_ID, UM_TENANT_ID)
            SELECT t1.UM_ID, 'uid', t1.UM_USER_NAME, 'default', -1234
                FROM TMP_UM_USER_UUID_CHUNK t1
                LEFT JOIN 
                    (SELECT DISTINCT(t8.UM_ID), t8.UM_USER_NAME
                    FROM TMP_UM_USER_UUID_CHUNK t8
                    LEFT JOIN UM_USER_ATTRIBUTE t9
                    ON t8.UM_ID = t9.UM_USER_ID
                    WHERE t9.UM_ATTR_NAME='uid') t2
                ON t1.UM_ID = t2.UM_ID
                WHERE t2.UM_ID IS NULL;
       
            -- if scim enable and not a force update then generated um_user_id wll be replaced with user's existing scim claim value
            IF (NOT forceUpdate AND scimEnable) THEN
                     
                CREATE TABLE TMP_UM_USER_SCIM AS
			    (SELECT t1.UM_ID, t1.UM_USER_ID,t2.UM_ATTR_VALUE FROM TMP_UM_USER_UUID_CHUNK t1 
			    INNER JOIN UM_USER_ATTRIBUTE t2 ON t1.UM_ID = t2.UM_USER_ID WHERE t2.UM_ATTR_NAME='scimId');

        		ALTER TABLE TMP_UM_USER_SCIM
		    	ADD PRIMARY KEY (UM_ID),
                ADD UNIQUE(UM_ATTR_VALUE);

                UPDATE UM_USER t1
                INNER JOIN TMP_UM_USER_SCIM t2
                ON t1.UM_ID = t2.UM_ID
                SET t1.UM_USER_ID = t2.UM_ATTR_VALUE;
                
                DROP TABLE TMP_UM_USER_SCIM;

            END IF;

            SET totalMigrated = totalMigrated + chunkCount;
            DROP TABLE TMP_UM_USER_UUID_CHUNK;

 		END WHILE;
 		
        DROP TABLE TMP_UM_USER_UUID;
      		
	END;

END;
//

DELIMITER ;


