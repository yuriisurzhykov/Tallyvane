package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import tallyvane.authentication.application.port.RecoveryCodeSets
import tallyvane.authentication.domain.RecoveryCodes
import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.Digest

/**
 * [RecoveryCodeSets] over the `authentication` schema in Postgres.
 *
 * Runs inside the caller's transaction (ADR-052), under the lock [PostgresTotpEnrollments.lock] takes.
 */
internal class PostgresRecoveryCodeSets : RecoveryCodeSets {
    override fun of(account: AccountId): RecoveryCodes? {
        val rows = RecoveryCodesTable.selectAll()
            .where { RecoveryCodesTable.accountId eq account.value }
            .orderBy(RecoveryCodesTable.position, SortOrder.ASC)
            .toList()
        if (rows.isEmpty()) {
            return null
        }
        check(rows.map { it[RecoveryCodesTable.position] } == (1..rows.size).toList()) {
            "The recovery codes of account ${account.value} are not numbered 1 to ${rows.size}. " +
                "RecoveryCodes.writeTo never says that, so the rows were changed by something else; issue a new set."
        }
        return RecoveryCodes.restore { record ->
            rows.forEach {
                record.code(
                    Digest(it[RecoveryCodesTable.digest], it[RecoveryCodesTable.pepperVersion]),
                    it[RecoveryCodesTable.spentAt],
                )
            }
        }
    }

    override fun keep(account: AccountId, codes: RecoveryCodes) {
        check(TotpEnrollmentsTable.selectAll().where { TotpEnrollmentsTable.accountId eq account.value }.any()) {
            "Recovery codes are kept for an account that has a TOTP enrolment kept first; account " +
                "${account.value} has none."
        }
        val told = mutableListOf<Pair<Digest, kotlin.time.Instant?>>()
        codes.writeTo { digest, spentAt -> told += digest to spentAt }
        RecoveryCodesTable.deleteWhere { RecoveryCodesTable.accountId eq account.value }
        RecoveryCodesTable.batchInsert(told.withIndex().toList()) { (index, code) ->
            val columns = DigestColumns().also { code.first.writeTo(it) }
            this[RecoveryCodesTable.accountId] = account.value
            this[RecoveryCodesTable.position] = index + 1
            this[RecoveryCodesTable.digest] = columns.bytes()
            this[RecoveryCodesTable.pepperVersion] = columns.version()
            this[RecoveryCodesTable.spentAt] = code.second
        }
    }

    override fun toString(): String = "PostgresRecoveryCodeSets(schema=authentication)"
}
