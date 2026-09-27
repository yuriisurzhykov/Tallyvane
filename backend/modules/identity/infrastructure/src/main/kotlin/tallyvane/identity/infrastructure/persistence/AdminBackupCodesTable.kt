package tallyvane.identity.infrastructure.persistence

internal object AdminBackupCodesTable : BackupCodeRowsTable("identity.admin_backup_codes", "admin_id", AdminsTable.id)
