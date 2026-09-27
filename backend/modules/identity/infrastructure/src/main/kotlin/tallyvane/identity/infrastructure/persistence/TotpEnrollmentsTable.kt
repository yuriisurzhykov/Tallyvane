package tallyvane.identity.infrastructure.persistence

internal object TotpEnrollmentsTable : TotpEnrollmentRowsTable("identity.totp_enrollments", "user_id", UsersTable.id)
