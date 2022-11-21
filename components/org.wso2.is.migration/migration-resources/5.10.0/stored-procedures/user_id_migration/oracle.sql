CREATE OR REPLACE PROCEDURE USERID_MIGRATION_SP
IS
BEGIN 
DECLARE 
    chunkSize INT:=1000;
    threshold INT:=9999999;
    totalMigrated INT:=0;
    chunkCount INT:=1;
    query varchar(200);
    offset INT:=0;
    forceUpdate BOOLEAN := FALSE;
    scimEnable BOOLEAN := TRUE;
    
	BEGIN
		DBMS_OUTPUT.ENABLE;
      		query:='CREATE TABLE TMP_UM_USER_UUID AS (SELECT UM_USER_NAME,UM_ID, UM_USER_ID FROM UM_USER)';
   		EXECUTE IMMEDIATE query;
        COMMIT;
     
        WHILE (chunkCount!=0 and totalMigrated < threshold ) LOOP
            
            offset:=totalMigrated+chunkSize;
                
            EXECUTE IMMEDIATE 'CREATE TABLE TMP_UM_USER_UUID_CHUNK AS (
                SELECT UM_USER_NAME,UM_ID, UM_USER_ID
                    FROM   (SELECT UM_USER_NAME,UM_ID, UM_USER_ID, rownum AS rnum
                            FROM   (SELECT UM_USER_NAME,UM_ID, UM_USER_ID
                                    FROM   TMP_UM_USER_UUID
                                    ORDER BY UM_ID)
                            WHERE rownum <= '||offset||')
                    WHERE  rnum > '||totalMigrated||')';
            COMMIT;
        
            EXECUTE IMMEDIATE'ALTER TABLE TMP_UM_USER_UUID_CHUNK
                                ADD CONSTRAINT pk PRIMARY KEY (UM_ID)';
            COMMIT;
       	    
               dbms_output.put_line('Migrated count: ' || totalMigrated || 'chunk size: ' || chunkSize );

            EXECUTE IMMEDIATE'  SELECT COUNT(*) FROM TMP_UM_USER_UUID_CHUNK' INTO chunkCount;
            COMMIT;
           
           IF forceUpdate THEN
       	   DBMS_OUTPUT.PUT_LINE('SCIM FOCE UPDATE update migration....');
    --    UPDATE UM_USER_ATTRIBUTE SET UM_ATTR_VALUE=? WHERE UM_USER_ID=@userId AND UM_ATTR_NAME='scimId'
--    enable IF focrce UPDATE IS TRUE only
            EXECUTE IMMEDIATE 'UPDATE 
                (SELECT t1.UM_ATTR_VALUE as UM_ATTR_VALUE, t2.UM_USER_NAME as UM_USER_NAME, t2.UM_USER_ID
                FROM UM_USER_ATTRIBUTE t1
                INNER JOIN TMP_UM_USER_UUID_CHUNK t2 
                ON t1.UM_USER_ID = t2.UM_ID
                WHERE t1.UM_ATTR_VALUE != t2.UM_USER_ID and t1.UM_ATTR_NAME=''scimId''
                ) t
                SET t.UM_ATTR_VALUE = t.UM_USER_ID';
            COMMIT;
			END IF;
	-- Insert scimId claim for users who didn't have a scimId    
            EXECUTE IMMEDIATE 'INSERT INTO UM_USER_ATTRIBUTE (UM_USER_ID, UM_ATTR_NAME, UM_ATTR_VALUE, UM_PROFILE_ID, UM_TENANT_ID)
                SELECT t1.UM_ID, ''scimId'', t1.UM_USER_ID, ''default'', -1234
                FROM TMP_UM_USER_UUID_CHUNK t1
                LEFT JOIN 
                    (SELECT DISTINCT(t8.UM_ID), t8.UM_USER_NAME
                    FROM TMP_UM_USER_UUID_CHUNK t8
                    LEFT JOIN UM_USER_ATTRIBUTE t9
                    ON t8.UM_ID = t9.UM_USER_ID
                    WHERE t9.UM_ATTR_NAME=''scimId'') t2
                ON t1.UM_ID = t2.UM_ID
                WHERE t2.UM_ID IS NULL';
            COMMIT;
           
        -- Insert uid claim for users who didn't have a uid
            EXECUTE IMMEDIATE 'INSERT INTO UM_USER_ATTRIBUTE (UM_USER_ID, UM_ATTR_NAME, UM_ATTR_VALUE, UM_PROFILE_ID, UM_TENANT_ID)
                SELECT t1.UM_ID, ''uid'', t1.UM_USER_NAME, ''default'', -1234
                FROM TMP_UM_USER_UUID_CHUNK t1
                LEFT JOIN 
                    (SELECT DISTINCT(t8.UM_ID), t8.UM_USER_NAME
                    FROM TMP_UM_USER_UUID_CHUNK t8
                    LEFT JOIN UM_USER_ATTRIBUTE t9
                    ON t8.UM_ID = t9.UM_USER_ID
                    WHERE t9.UM_ATTR_NAME=''uid'') t2
                ON t1.UM_ID = t2.UM_ID
                WHERE t2.UM_ID IS NULL';
            COMMIT;
       
             IF (NOT forceUpdate AND scimEnable) THEN
                     
               EXECUTE IMMEDIATE 'CREATE TABLE TMP_UM_USER_SCIM AS
						 (SELECT t1.UM_ID, t1.UM_USER_ID,t2.UM_ATTR_VALUE FROM TMP_UM_USER_UUID_CHUNK t1 
							INNER JOIN UM_USER_ATTRIBUTE t2 ON t1.UM_ID = t2.UM_USER_ID WHERE t2.UM_ATTR_NAME=''scimId'')';
            COMMIT;
			EXECUTE IMMEDIATE'ALTER TABLE TMP_UM_USER_SCIM
								ADD CONSTRAINT pknew PRIMARY KEY (UM_ID)
                                ADD CONSTRAINT newkey UNIQUE (UM_ATTR_VALUE)';
                                           COMMIT;

               EXECUTE IMMEDIATE 'UPDATE 
                (SELECT t2.UM_ATTR_VALUE, t1.UM_USER_ID as UUID
                FROM UM_USER t1
                INNER JOIN TMP_UM_USER_SCIM t2 
                ON t1.UM_ID = t2.UM_ID
                ) t
                SET t.UUID = t.UM_ATTR_VALUE';
               COMMIT;

			   EXECUTE IMMEDIATE 'DROP TABLE TMP_UM_USER_SCIM';
            COMMIT;

             END IF;
            totalMigrated := totalMigrated + chunkCount;
            EXECUTE IMMEDIATE 'DROP TABLE TMP_UM_USER_UUID_CHUNK';
            COMMIT;

 		END LOOP;
 		
        EXECUTE IMMEDIATE 'DROP TABLE TMP_UM_USER_UUID';
      
 		COMMIT;
		
	END;

END;
