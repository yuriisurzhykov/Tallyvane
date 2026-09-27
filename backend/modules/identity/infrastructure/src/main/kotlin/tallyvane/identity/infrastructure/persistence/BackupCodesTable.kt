package tallyvane.identity.infrastructure.persistence

internal object BackupCodesTable : BackupCodeRowsTable("identity.backup_codes", "user_id", UsersTable.id)
