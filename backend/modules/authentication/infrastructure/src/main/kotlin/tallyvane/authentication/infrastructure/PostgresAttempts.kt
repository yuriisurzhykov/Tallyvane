package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import tallyvane.authentication.application.AttemptSaveOutcome
import tallyvane.authentication.application.port.Attempts
import tallyvane.authentication.domain.Attempt
import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.Purpose
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val CLAIM_ATTEMPT = """
    insert into authentication.attempts (id, purpose, started_at) values (?, ?, ?)
    on conflict (id) do nothing
"""

/**
 * [Attempts] over the `authentication` schema in Postgres, through Exposed's DSL (ADR-049).
 *
 * Runs inside the caller's transaction and never opens one: the use case decides where a
 * transaction begins (ADR-052). The attempt is told to [Attempt.writeTo] a listener that fills rows,
 * and brought back by [Attempt.restore] from a replay of those rows; no list of an attempt is ever
 * read from the attempt itself (ADR-085).
 *
 * ### How two requests at once are kept apart
 *
 * [save] first makes sure a row for the attempt exists, with `ON CONFLICT DO NOTHING` so that two
 * first saves cannot both insert it, then locks that row with `SELECT ... FOR UPDATE`. Whoever holds
 * the lock reads what is kept as it is *now*, which under `READ COMMITTED` includes whatever the
 * request before it committed, and only then decides whether its attempt contains all of it. A
 * request that does not is told [AttemptSaveOutcome.Superseded] and changes nothing.
 *
 * The primary keys on `(attempt_id, position)` are the last line behind that lock: were two writers
 * ever to append the same position, the second would fail rather than overwrite.
 */
internal class PostgresAttempts : Attempts {
    private val words = StoredWords()

    override fun find(id: Uuid): Attempt? = rowsOf(id, locking = false)?.let { rows ->
        Attempt.restore { record -> rows.replayInto(record) }
    }

    override fun save(id: Uuid, attempt: Attempt): AttemptSaveOutcome {
        val told = Rows(attempt)
        told.replayInto(Claiming(id))
        val kept = checkNotNull(rowsOf(id, locking = true)) {
            "The attempt $id vanished between being claimed and being locked in one transaction. " +
                "Something deleted it; attempts are only removed by their cleanup, never mid-request."
        }
        if (!told.continues(kept)) {
            return AttemptSaveOutcome.Superseded
        }
        told.replayBeyond(kept, Appending(id, kept))
        return AttemptSaveOutcome.Saved
    }

    override fun toString(): String = "PostgresAttempts(schema=authentication)"

    private fun rowsOf(id: Uuid, locking: Boolean): Rows? {
        val head = AttemptsTable.selectAll().where { AttemptsTable.id eq id }
            .let { query -> if (locking) query.forUpdate() else query }
            .singleOrNull() ?: return null
        val verified = AttemptVerifiedFactorsTable.selectAll()
            .where { AttemptVerifiedFactorsTable.attemptId eq id }
            .orderBy(AttemptVerifiedFactorsTable.position, SortOrder.ASC)
            .toList()
        val failures = AttemptFailuresTable.selectAll()
            .where { AttemptFailuresTable.attemptId eq id }
            .orderBy(AttemptFailuresTable.position, SortOrder.ASC)
            .toList()
        check(verified.map { it[AttemptVerifiedFactorsTable.position] } == (1..verified.size).toList()) {
            refused(id, "attempt_verified_factors")
        }
        check(failures.map { it[AttemptFailuresTable.position] } == (1..failures.size).toList()) {
            refused(id, "attempt_failures")
        }
        return Rows(
            purpose = words.purposeFrom(head[AttemptsTable.purpose]),
            startedAt = head[AttemptsTable.startedAt],
            verified = verified.map {
                words.kindFrom(it[AttemptVerifiedFactorsTable.kind]) to it[AttemptVerifiedFactorsTable.verifiedAt]
            },
            failures = failures.map { it[AttemptFailuresTable.failedAt] },
        )
    }

    private fun refused(id: Uuid, table: String): String =
        "The rows of authentication.$table for attempt $id are not numbered 1, 2, 3 without a gap. " +
            "Only Attempt.writeTo through this adapter writes them, so something else changed them; " +
            "attempts are short-lived, so delete the attempt and let the person sign in again."

    /**
     * Inserts the attempt's row if there is none, from the first thing the attempt tells.
     *
     * Written as the statement it is. Exposed's `insertIgnore` generates a conflict target that
     * Postgres refuses when the table name carries a schema, and a statement that names its schema and
     * its conflict rule outright is also the one reviewers can read.
     */
    private class Claiming(private val id: Uuid) : Attempt.Record {
        private val words = StoredWords()

        override fun started(purpose: Purpose, at: Instant) {
            TransactionManager.current().exec(
                CLAIM_ATTEMPT,
                listOf(
                    AttemptsTable.id.columnType to id,
                    AttemptsTable.purpose.columnType to words.of(purpose),
                    AttemptsTable.startedAt.columnType to at,
                ),
            )
        }

        override fun verified(kind: FactorKind, at: Instant) = Unit

        override fun failed(at: Instant) = Unit
    }

    /**
     * Inserts what an attempt gained, numbering it after what was kept.
     */
    private class Appending(private val id: Uuid, kept: Rows) : Attempt.Record {
        private val words = StoredWords()
        private var verifiedAlready = kept.verifiedCount()
        private var failedAlready = kept.failureCount()

        override fun started(purpose: Purpose, at: Instant) = Unit

        override fun verified(kind: FactorKind, at: Instant) {
            verifiedAlready += 1
            AttemptVerifiedFactorsTable.insert {
                it[attemptId] = id
                it[position] = verifiedAlready
                it[AttemptVerifiedFactorsTable.kind] = words.of(kind)
                it[verifiedAt] = at
            }
        }

        override fun failed(at: Instant) {
            failedAlready += 1
            AttemptFailuresTable.insert {
                it[attemptId] = id
                it[position] = failedAlready
                it[failedAt] = at
            }
        }
    }

    /**
     * An attempt as rows hold it: its start, then its factors and wrong answers in order, with every
     * instant at the precision Postgres keeps, so rows read back compare equal to the attempt that
     * was written.
     */
    private class Rows(
        private val purpose: Purpose,
        private val startedAt: Instant,
        private val verified: List<Pair<FactorKind, Instant>>,
        private val failures: List<Instant>,
    ) {
        constructor(attempt: Attempt) : this(Telling().also { attempt.writeTo(it) })

        private constructor(telling: Telling) : this(
            telling.purpose(),
            telling.startedAt(),
            telling.verifiedFactors(),
            telling.failureTimes(),
        )

        fun verifiedCount(): Int = verified.size

        fun failureCount(): Int = failures.size

        /**
         * Whether these rows hold everything [shorter] does, unchanged and in order, and may hold more.
         */
        fun continues(shorter: Rows): Boolean = purpose == shorter.purpose &&
            startedAt == shorter.startedAt &&
            verified.take(shorter.verified.size) == shorter.verified &&
            failures.take(shorter.failures.size) == shorter.failures

        fun replayInto(record: Attempt.Record) {
            record.started(purpose, startedAt)
            verified.forEach { (kind, at) -> record.verified(kind, at) }
            failures.forEach { record.failed(it) }
        }

        /**
         * Only what these rows hold beyond [kept], which [continues] it.
         */
        fun replayBeyond(kept: Rows, record: Attempt.Record) {
            verified.drop(kept.verified.size).forEach { (kind, at) -> record.verified(kind, at) }
            failures.drop(kept.failures.size).forEach { record.failed(it) }
        }
    }

    /**
     * Collects what an attempt tells, cutting each instant to the microsecond.
     */
    private class Telling : Attempt.Record {
        private val began = mutableListOf<Pair<Purpose, Instant>>()
        private val verified = mutableListOf<Pair<FactorKind, Instant>>()
        private val failures = mutableListOf<Instant>()

        override fun started(purpose: Purpose, at: Instant) {
            began += purpose to at.toMicroseconds()
        }

        override fun verified(kind: FactorKind, at: Instant) {
            verified += kind to at.toMicroseconds()
        }

        override fun failed(at: Instant) {
            failures += at.toMicroseconds()
        }

        fun purpose(): Purpose = began.single().first

        fun startedAt(): Instant = began.single().second

        fun verifiedFactors(): List<Pair<FactorKind, Instant>> = verified.toList()

        fun failureTimes(): List<Instant> = failures.toList()

        private fun Instant.toMicroseconds(): Instant = Instant.fromEpochSeconds(
            epochSeconds,
            nanosecondsOfSecond / NANOSECONDS_PER_MICROSECOND * NANOSECONDS_PER_MICROSECOND,
        )

        private companion object {
            const val NANOSECONDS_PER_MICROSECOND = 1000
        }
    }
}
