package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import tallyvane.authentication.application.port.EnrollmentCensus
import tallyvane.authentication.domain.Enrollment
import tallyvane.authentication.domain.FactorKind

/**
 * [EnrollmentCensus] over the `authentication` schema in Postgres: one grouped query, however many accounts there
 * are.
 *
 * Counts what [Enrollment.of] counts: TOTP while its enrolment is active, and recovery codes while at least one is
 * unspent. Runs inside the caller's transaction (ADR-052).
 */
internal class PostgresEnrollmentCensus : EnrollmentCensus {
    override fun enrolled(): Map<Enrollment, Int> {
        val counted = TransactionManager.current().exec(COUNTING) { rows ->
            val found = mutableMapOf<Enrollment, Int>()
            while (rows.next()) {
                val kinds = setOfNotNull(
                    FactorKind.Totp.takeIf { rows.getBoolean(1) },
                    FactorKind.RecoveryCode.takeIf { rows.getBoolean(2) },
                )
                found.merge(Enrollment(kinds), rows.getInt(3), Int::plus)
            }
            found.toMap()
        }
        return counted.orEmpty()
    }

    override fun toString(): String = "PostgresEnrollmentCensus(schema=authentication)"

    private companion object {
        const val COUNTING = """
            select t.account_id is not null as has_totp, c.account_id is not null as has_codes, count(*)
            from (select account_id from authentication.totp_enrollments where standing = 'active') t
                     full outer join (select distinct account_id
                                      from authentication.recovery_codes
                                      where spent_at is null) c on c.account_id = t.account_id
            group by 1, 2
        """
    }
}
