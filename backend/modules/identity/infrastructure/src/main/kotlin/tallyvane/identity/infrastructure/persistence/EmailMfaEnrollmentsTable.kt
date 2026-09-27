package tallyvane.identity.infrastructure.persistence

internal object EmailMfaEnrollmentsTable :
    EmailMfaEnrollmentRowsTable("identity.email_mfa_enrollments", "user_id", UsersTable.id)
