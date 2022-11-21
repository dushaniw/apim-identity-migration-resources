The following steps are required only if the "UserIDMigrator" step takes too long via the migration client.

1. Comment out the UserIDMigrator section as shown below in the <identity-migration-resources>/components/org.wso2.is.migration/migration-resources/migration-config.yaml file
```
version: "5.10.0"
   migratorConfigs:
   -
....
  #  -
    #  name: "UserIDMigrator"
    #  order: 10
    #  parameters:
       # Migrate all the tenants and all the user store domains in it.
       # migrateAll: true
       # Absolute path for the dry report. This is required in the dry run mode.
       # reportPath:
       # If migrating only few tenants, this configuration should be repeated for each tenant. (Optional)
       # tenant1:
         # Domain name of the tenant. (Mandatory)
         # tenantDomain: carbon.super
         # Number of users to be updated in each iteration. (Optional)
         # increment: 100
         # Where should the migration should start from (Offset). This is useful if the migration stopped middle and needs to restart. (Optional)
         # startingPoint: 0
         # Whether SCIM enabled for user stores in this tenant. (Optional)
         # scimEnabled: false
         # List of comma separated domain names which should be migrated in this domain. (Optional)
         # migratingDomains: "PRIMARY"
         # Mark whether user IDs should be updated even though there is already an ID there. (Optional)
         # forceUpdateUserId: true


 -
   version: "5.11.0"
```
2. Then run the migration
3. Execute the relevent stored procedure "<migration-resources>/5.10.0/stored-procedures/user_id_migration/<db_type>.sql" to migrate the userids mannually.
