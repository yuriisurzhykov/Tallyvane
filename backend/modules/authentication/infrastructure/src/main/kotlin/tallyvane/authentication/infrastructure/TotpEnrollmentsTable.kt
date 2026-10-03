package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.core.Table

/**
 * `authentication.totp_enrollments`: the TOTP enrolment of an account, its seed sealed.
 */
internal object TotpEnrollmentsTable : Table("authentication.totp_enrollments") {
    val accountId = uuid("account_id")
    val sealedSeed = text("sealed_seed")
    val standing = text("standing")
    val lastAcceptedStep = long("last_accepted_step").nullable()

    override val primaryKey = PrimaryKey(accountId)
}
