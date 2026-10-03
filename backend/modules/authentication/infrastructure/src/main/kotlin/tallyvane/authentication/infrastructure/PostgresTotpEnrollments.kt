package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import tallyvane.authentication.application.port.TotpEnrollments
import tallyvane.authentication.domain.TotpEnrollment
import tallyvane.identity.contract.AccountId

/**
 * [TotpEnrollments] over the `authentication` schema in Postgres, with the seed sealed by [cipher].
 *
 * Runs inside the caller's transaction (ADR-052). [lock] reads `for update`, so a second request that
 * is about to change the same enrolment waits until the first commits (ADR-093).
 */
internal class PostgresTotpEnrollments(private val cipher: SecretCipher) : TotpEnrollments {
    override fun find(account: AccountId): TotpEnrollment? = read(account, locking = false)

    override fun lock(account: AccountId): TotpEnrollment? = read(account, locking = true)

    override fun keep(account: AccountId, enrollment: TotpEnrollment) {
        enrollment.writeTo { seed, standing, lastAcceptedStep ->
            val sealed = cipher.seal(seed)
            val changed = TotpEnrollmentsTable.update({ TotpEnrollmentsTable.accountId eq account.value }) {
                it[sealedSeed] = sealed
                it[TotpEnrollmentsTable.standing] = standingWord(standing)
                it[TotpEnrollmentsTable.lastAcceptedStep] = lastAcceptedStep
            }
            if (changed == 0) {
                TotpEnrollmentsTable.insert {
                    it[accountId] = account.value
                    it[sealedSeed] = sealed
                    it[TotpEnrollmentsTable.standing] = standingWord(standing)
                    it[TotpEnrollmentsTable.lastAcceptedStep] = lastAcceptedStep
                }
            }
        }
    }

    override fun forget(account: AccountId) {
        // The recovery codes go with the enrolment: their foreign key cascades.
        TotpEnrollmentsTable.deleteWhere { TotpEnrollmentsTable.accountId eq account.value }
    }

    override fun toString(): String = "PostgresTotpEnrollments(schema=authentication)"

    private fun read(account: AccountId, locking: Boolean): TotpEnrollment? {
        val row = TotpEnrollmentsTable.selectAll()
            .where { TotpEnrollmentsTable.accountId eq account.value }
            .let { query -> if (locking) query.forUpdate() else query }
            .singleOrNull() ?: return null
        return TotpEnrollment.restore { record ->
            record.kept(
                cipher.open(row[TotpEnrollmentsTable.sealedSeed]),
                standingFrom(row[TotpEnrollmentsTable.standing]),
                row[TotpEnrollmentsTable.lastAcceptedStep],
            )
        }
    }

    private fun standingWord(standing: TotpEnrollment.Standing): String = when (standing) {
        TotpEnrollment.Standing.Pending -> "pending"
        TotpEnrollment.Standing.Active -> "active"
        TotpEnrollment.Standing.Retired -> "retired"
    }

    private fun standingFrom(word: String): TotpEnrollment.Standing =
        TotpEnrollment.Standing.entries.singleOrNull { standingWord(it) == word }
            ?: error(
                "The database holds the standing '$word', which the code does not know. A migration added " +
                    "a value without the code that reads it.",
            )
}
