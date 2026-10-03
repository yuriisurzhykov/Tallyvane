package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import tallyvane.authentication.application.port.AccountFailures
import tallyvane.authentication.domain.AccountGuesses
import tallyvane.identity.contract.AccountId
import kotlin.time.Instant

/**
 * [AccountFailures] over the `authentication` schema in Postgres.
 *
 * Runs inside the caller's transaction (ADR-052).
 */
internal class PostgresAccountFailures : AccountFailures {
    override fun recent(account: AccountId, since: Instant): AccountGuesses = AccountGuesses.at(
        SecondFactorFailuresTable.selectAll()
            .where {
                (SecondFactorFailuresTable.accountId eq account.value) and
                    (SecondFactorFailuresTable.failedAt greaterEq since)
            }
            .map { it[SecondFactorFailuresTable.failedAt] },
    )

    override fun record(account: AccountId, at: Instant) {
        SecondFactorFailuresTable.deleteWhere {
            (accountId eq account.value) and (failedAt less at - AccountGuesses.WINDOW)
        }
        SecondFactorFailuresTable.insert {
            it[accountId] = account.value
            it[failedAt] = at
        }
    }

    override fun toString(): String = "PostgresAccountFailures(schema=authentication)"
}
