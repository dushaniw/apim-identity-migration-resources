CREATE OR ALTER PROCEDURE change_primary_key @TableName NVARCHAR(30)
AS
    begin
        DECLARE @PkConstraintName NVARCHAR(255)
        DECLARE @DropPrimaryKeyQuery NVARCHAR(255)
        DECLARE @AddPrimaryKeyQuery NVARCHAR(255)

        SELECT @PkConstraintName = NAME FROM SYSOBJECTS WHERE XTYPE = 'PK' AND PARENT_OBJ = OBJECT_ID(@TableName)
        IF @PkConstraintName IS NOT NULL
        begin
            SET @DropPrimaryKeyQuery = 'ALTER TABLE ' + @TableName + ' DROP CONSTRAINT ' + @PkConstraintName
            EXEC(@DropPrimaryKeyQuery)
        end

        SET @AddPrimaryKeyQuery = 'ALTER TABLE ' + @TableName +  ' ADD ID INTEGER NOT NULL IDENTITY PRIMARY KEY'
        EXEC(@AddPrimaryKeyQuery)
    end;

EXEC change_primary_key @TableName = 'REG_RESOURCE_COMMENT';

EXEC change_primary_key @TableName = 'REG_RESOURCE_RATING';

EXEC change_primary_key @TableName = 'REG_RESOURCE_TAG';

EXEC change_primary_key @TableName = 'REG_RESOURCE_PROPERTY';

EXEC change_primary_key @TableName = 'UM_SHARED_USER_ROLE';
