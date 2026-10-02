package tallyvane.platform.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import tallyvane.platform.idempotency.Answer
import tallyvane.platform.idempotency.Claim
import tallyvane.platform.idempotency.Earlier
import tallyvane.platform.idempotency.Ledger
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict

/**
 * [Ledger] over `platform.idempotency_keys`.
 *
 * Each method runs in a transaction of its own, on [transactions], which must be the runner that does
 * *not* take claims: this is asked around the request's transaction, from inside the request's
 * coroutine, so a runner that took the claim in the context would try to claim the request a second
 * time.
 */
internal class PostgresLedger(private val transactions: TransactionRunner, private val clock: Clock) : Ledger {
    override suspend fun earlier(claim: Claim): Earlier {
        val facts = ClaimFacts.of(claim)
        return transactions.inTransaction {
            val kept = IdempotencyKeysTable.selectAll()
                .where { standing(facts) }
                .singleOrNull()
            Verdict.Commit(
                when {
                    kept == null -> Earlier.None
                    !kept[IdempotencyKeysTable.fingerprint].contentEquals(facts.fingerprint) -> Earlier.Different
                    kept[IdempotencyKeysTable.outcome] == REPLAYED -> Earlier.Replay(answerOf(kept))
                    kept[IdempotencyKeysTable.outcome] == WITHHELD -> Earlier.Withheld
                    else -> Earlier.Unanswered
                },
            )
        }
    }

    override suspend fun record(claim: Claim, answer: Answer) {
        val facts = ClaimFacts.of(claim)
        transactions.inTransaction {
            IdempotencyKeysTable.update({ standing(facts) and IdempotencyKeysTable.outcome.isNull() }) { row ->
                answer.tell(Storing(row))
            }
            Verdict.Commit(Unit)
        }
    }

    override suspend fun withhold(claim: Claim) {
        val facts = ClaimFacts.of(claim)
        transactions.inTransaction {
            IdempotencyKeysTable.update({ standing(facts) and IdempotencyKeysTable.outcome.isNull() }) { row ->
                row[outcome] = WITHHELD
            }
            Verdict.Commit(Unit)
        }
    }

    override suspend fun forgetExpired(): Int = transactions.inTransaction {
        Verdict.Commit(IdempotencyKeysTable.deleteWhere { expiresAt lessEq clock.now() })
    }

    override fun toString(): String = "PostgresLedger(table=platform.idempotency_keys)"

    private fun answerOf(kept: ResultRow): Answer = Answer(
        status = checkNotNull(kept[IdempotencyKeysTable.status]).toInt(),
        contentType = kept[IdempotencyKeysTable.contentType],
        body = checkNotNull(kept[IdempotencyKeysTable.body]),
    )

    private fun standing(facts: ClaimFacts) = (IdempotencyKeysTable.owner eq facts.owner) and
        (IdempotencyKeysTable.key eq facts.key) and
        (IdempotencyKeysTable.expiresAt greater clock.now())

    /**
     * Turns what an answer says into the columns that keep it.
     */
    private class Storing(private val row: UpdateStatement) : Answer.Record {
        override fun answered(status: Int, contentType: String?, body: ByteArray) {
            row[IdempotencyKeysTable.outcome] = REPLAYED
            row[IdempotencyKeysTable.status] = status.toShort()
            row[IdempotencyKeysTable.contentType] = contentType
            row[IdempotencyKeysTable.body] = body
        }
    }

    private companion object {
        const val REPLAYED = "replayed"

        const val WITHHELD = "withheld"
    }
}
