CREATE OR REPLACE PROCEDURE USERID_MIGRATION_SP ()
     DYNAMIC RESULT SETS 0
     MODIFIES SQL DATA
     LANGUAGE SQL
	BEGIN
	    DECLARE chunkSize INT;
	    DECLARE threshold INT;
	    DECLARE totalMigrated ;
	    DECLARE chunkCount INT;
	    DECLARE query varchar(200);
	    DECLARE offset INT;
	    DECLARE forceUpdate BOOLEAN;
	    DECLARE scimEnable BOOLEAN;
	   
	    SET chunkSize = 1000;
	    SET threshold = 9999999;
	    SET totalMigrated = 0;
	    SET chunkCount = 1;
	    SET offset = 0;
	    SET forceUpdate =  FALSE;
	    SET scimEnable = TRUE; 
   
	
      	CREATE TABLE TMP_UM_USER_UUID AS (SELECT UM_USER_NAME,UM_ID, UM_USER_ID FROM UM_USER) WITH DATA;
     
        WHILE (chunkCount!=0 and totalMigrated < threshold ) DO
            
          SET offset=totalMigrated+chunkSize;
                
          CREATE TABLE TMP_UM_USER_UUID_CHUNK LIKE TMP_UM_USER_UUID;
          
       	  INSERT INTO TMP_UM_USER_UUID_CHUNK SELECT * FROM TMP_UM_USER_UUID LIMIT offset, chunkSize;                            
        
          ALTER TABLE TMP_UM_USER_UUID_CHUNK
          ADD CONSTRAINT pk PRIMARY KEY (UM_ID);
       	    
          SET chunkCount = (SELECT COUNT FROM TMP_UM_USER_UUID_CHUNK);
           
         -- If it is force update then update the existing scim id with newly generated um_user_id in um_user table
          IF forceUpdate THEN

            UPDATE UM_USER_ATTRIBUTE as t1
            SET UM_ATTR_VALUE = t2.UM_USER_ID
			FROM TMP_UM_USER_UUID_CHUNK AS t2 
	        WHERE t1.UM_USER_ID = t2.UM_ID
	        and t1.UM_ATTR_VALUE != t2.UM_USER_ID
	        and t1.UM_ATTR_NAME='scimId';
	       
		  END IF;
		  -- insert scim id claim for the users who doesnt have one
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
               
             -- insert uid claim for the users who doesnt have one
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

              UPDATE UM_USER AS t1
              SET UM_USER_ID = t2.UM_ATTR_VALUE;
              FROM TMP_UM_USER_SCIM as t2
              where t1.UM_ID = t2.UM_ID
              
			  DROP TABLE TMP_UM_USER_SCIM;

            END IF;
            
           SET totalMigrated = totalMigrated + chunkCount;
           DROP TABLE TMP_UM_USER_UUID_CHUNK;

 		END WHILE;
 		
        DROP TABLE TMP_UM_USER_UUID;
      
 		COMMIT;
		
END;



