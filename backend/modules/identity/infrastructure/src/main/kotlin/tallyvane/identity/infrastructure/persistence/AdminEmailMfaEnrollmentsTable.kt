package tallyvane.identity.infrastructure.persistence

internal object AdminEmailMfaEnrollmentsTable :
    EmailMfaEnrollmentRowsTable("identity.admin_email_mfa_enrollments", "admin_id", AdminsTable.id)
