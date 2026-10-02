package tallyvane.platform.persistence

import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import tallyvane.platform.idempotency.Claim
import tallyvane.platform.idempotency.ClaimBusy
import tallyvane.platform.idempotency.ClaimedElsewhere
import tallyvane.platform.idempotency.Claims
import tallyvane.platform.kernel.Clock
import kotlin.time.Duration.Companion.hours

private const val TAKE_CLAIM = """
    insert into platform.idempotency_keys as kept (owner, key, fingerprint, created_at, expires_at)
    values (?, ?, ?, ?, ?)
    on conflict (owner, key) do update
        set fingerprint = excluded.fingerprint,
            created_at = excluded.created_at,
            expires_at = excluded.expires_at,
            outcome = null,
            status = null,
            content_type = null,
            body = null
        where kept.expires_at <= ?
    returning key
"""

/**
 * [Claims] over `platform.idempotency_keys`, inside the transaction the caller opened.
 *
 * The statement is one atomic decision. An unused key is inserted. A key whose day is over is taken
 * over, its old answer cleared. A live key refuses it, and the statement then returns no row.
 *
 * ### What waiting looks like
 *
 * If another transaction has inserted the same key and not finished, this statement does not fail and
 * does not skip: Postgres makes it wait on the other transaction, which is the whole of Q3. When that
 * one commits the key is live and this statement returns nothing, so [take] says [ClaimedElsewhere]. When
 * it rolls back the insert goes ahead. The wait is bounded by `lock_timeout`, 3 seconds on every pooled
 * connection (ADR-058), and when it runs out Postgres answers `55P03`, which becomes [ClaimBusy].
 */
internal class PostgresClaims(private val clock: Clock) : Claims {
    override fun take(claim: Claim) {
        val facts = ClaimFacts.of(claim)
        val now = clock.now()
        val taken = try {
            TransactionManager.current().exec(
                TAKE_CLAIM,
                listOf<Pair<IColumnType<*>, Any?>>(
                    IdempotencyKeysTable.owner.columnType to facts.owner,
                    IdempotencyKeysTable.key.columnType to facts.key,
                    IdempotencyKeysTable.fingerprint.columnType to facts.fingerprint,
                    IdempotencyKeysTable.createdAt.columnType to now,
                    IdempotencyKeysTable.expiresAt.columnType to now + LIFETIME,
                    IdempotencyKeysTable.expiresAt.columnType to now,
                ),
                // `returning` makes it a query to the driver, whatever word the statement starts with.
                StatementType.SELECT,
            ) { rows -> rows.next() }
        } catch (failure: ExposedSQLException) {
            throw if (failure.sqlState == LOCK_NOT_AVAILABLE) ClaimBusy() else failure
        }
        if (taken != true) {
            throw ClaimedElsewhere()
        }
    }

    override fun toString(): String = "PostgresClaims(table=platform.idempotency_keys)"

    private companion object {
        /**
         * How long a claim holds a key (ADR-086).
         */
        val LIFETIME = 24.hours

        /**
         * SQLSTATE of a lock wait that ran out.
         */
        const val LOCK_NOT_AVAILABLE = "55P03"
    }
}
