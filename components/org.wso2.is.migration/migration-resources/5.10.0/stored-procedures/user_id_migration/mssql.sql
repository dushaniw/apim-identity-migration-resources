DROP PROCEDURE IF EXISTS USERID_MIGRATION_SP;

CREATE PROCEDURE USERID_MIGRATION_SP
AS
BEGIN
 
    DECLARE @chunkSize INT
    DECLARE @threshold INT
    DECLARE @totalMigrated INT
    DECLARE @chunkCount INT
    DECLARE @query varchar(200)
    DECLARE @offset INT
    DECLARE @forceUpdate bit
    DECLARE @scimEnable bit 
   
    SET @chunkSize = 1000
    SET @threshold = 9999999
    SET @totalMigrated = 0
    SET @chunkCount = 1
    SET @offset = 0
    SET @forceUpdate =  0
    SET @scimEnable =  1
    
	BEGIN
		
		CREATE TABLE TMP_UM_USER_UUID (
        UM_USER_NAME varchar(255) NOT NULL UNIQUE,
        UM_ID int PRIMARY KEY,
        UUID varchar(36) NOT NULL
        )
        INSERT INTO TMP_UM_USER_UUID SELECT UM_USER_NAME, UM_ID, UM_USER_ID FROM UM_USER
        
        WHILE (@chunkCount!=0 and @totalMigrated < @threshold ) BEGIN
            
            SET @offset=@totalMigrated+@chunkSize    
            
            Select * into TMP_UM_USER_UUID_CHUNK  from  TMP_UM_USER_UUID ORDER BY UM_ID OFFSET @totalMigrated ROWS FETCH NEXT @chunkSize ROWS ONLY
            
            ALTER TABLE TMP_UM_USER_UUID_CHUNK
            ADD CONSTRAINT pk PRIMARY KEY (UM_ID)
       	    
		    SET @chunkCount = (select COUNT(*) from TMP_UM_USER_UUID_CHUNK)
		    
            -- If it is force update then update the existing scim id with newly generated um_user_id in um_user table
            IF @forceUpdate = 1
                BEGIN
                    UPDATE  t1 
                    SET t1.UM_ATTR_VALUE = t2.UUID
                    FROM UM_USER_ATTRIBUTE AS t1
                    INNER JOIN TMP_UM_USER_UUID_CHUNK AS t2 
                    ON t1.UM_USER_ID = t2.UM_ID
                    WHERE t1.UM_ATTR_VALUE != t2.UUID
                    and t1.UM_ATTR_NAME='scimId'
                END 
		  -- Insert scim id claim for the users who doesnt have one
			INSERT INTO UM_USER_ATTRIBUTE (UM_USER_ID, UM_ATTR_NAME, UM_ATTR_VALUE, UM_PROFILE_ID, UM_TENANT_ID)
                SELECT t1.UM_ID, 'scimId', t1.UUID, 'default', -1234
                FROM TMP_UM_USER_UUID_CHUNK t1
                LEFT JOIN 
                    (SELECT DISTINCT(t8.UM_ID), t8.UM_USER_NAME
                    FROM TMP_UM_USER_UUID_CHUNK t8
                    LEFT JOIN UM_USER_ATTRIBUTE t9
                    ON t8.UM_ID = t9.UM_USER_ID
                    WHERE t9.UM_ATTR_NAME='scimId') t2
                ON t1.UM_ID = t2.UM_ID
                WHERE t2.UM_ID IS NULL
            
            -- Insert uid claim for the users who doesnt have one
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
                WHERE t2.UM_ID IS NULL
            
            -- If scim enable and not a force update then generated um_user_id wll be replaced with user's existing scim claim value 
            IF (@forceUpdate = 0 AND @scimEnable = 1)
                BEGIN 
                    Select t1.UM_ID, t1.UUID,t2.UM_ATTR_VALUE into TMP_UM_USER_SCIM 
                    FROM TMP_UM_USER_UUID_CHUNK as t1 
                    INNER JOIN UM_USER_ATTRIBUTE as t2
                    ON t1.UM_ID = t2.UM_USER_ID 
                    WHERE t2.UM_ATTR_NAME='scimId'
			 
                    ALTER TABLE TMP_UM_USER_SCIM
                    ADD CONSTRAINT pky PRIMARY KEY (UM_ID)
                    
                    ALTER TABLE TMP_UM_USER_SCIM
                    ADD CONSTRAINT unk UNIQUE (UM_ATTR_VALUE)   

                    UPDATE t1
                    SET t1.UM_USER_ID = t2.UM_ATTR_VALUE
                    FROM UM_USER AS t1
                    INNER JOIN TMP_UM_USER_SCIM AS t2
                    ON t1.UM_ID = t2.UM_ID
              
		            DROP TABLE TMP_UM_USER_SCIM

                END 

            SET @totalMigrated = @totalMigrated + @chunkCount
            DROP TABLE TMP_UM_USER_UUID_CHUNK
            
 		END
        DROP TABLE TMP_UM_USER_UUID	
	END
END
GO

