package tallyvane.identity.infrastructure.persistence

internal object AdminTotpEnrollmentsTable :
    TotpEnrollmentRowsTable("identity.admin_totp_enrollments", "admin_id", AdminsTable.id)
