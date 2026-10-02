package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import tallyvane.authentication.application.AttemptSaveOutcome
import tallyvane.authentication.application.port.Attempts
import tallyvane.authentication.domain.Attempt
import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.Purpose
import tallyvane.platform.kernel.Digest
import tallyvane.platform.kernel.IdGenerator
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val CLAIM_ATTEMPT = """
    insert into authentication.attempts (id, purpose, started_at, secret_digest, pepper_version)
    values (?, ?, ?, ?, ?)
    on conflict (secret_digest) do nothing
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
internal class PostgresAttempts(private val rows: AttemptRows, private val ids: IdGenerator) : Attempts {
    private val words = StoredWords()

    override fun find(key: Digest): Attempt? = rowsOf(key, locking = false)?.let { found ->
        Attempt.restore { record -> found.replayInto(record) }
    }

    override fun save(key: Digest, attempt: Attempt): AttemptSaveOutcome {
        val told = Rows(attempt)
        told.replayInto(Claiming(key, ids.next()))
        val kept = checkNotNull(rowsOf(key, locking = true)) {
            "The attempt under $key vanished between being claimed and being locked in one transaction. " +
                "Something deleted it; attempts are only removed by their cleanup, never mid-request."
        }
        if (!told.continues(kept)) {
            return AttemptSaveOutcome.Superseded
        }
        told.replayBeyond(kept, Appending(kept.id(), kept))
        return AttemptSaveOutcome.Saved
    }

    override fun forget(key: Digest) {
        rows.idOf(key)?.let { id -> AttemptsTable.deleteWhere { AttemptsTable.id eq id } }
    }

    override fun toString(): String = "PostgresAttempts(schema=authentication)"

    private fun rowsOf(key: Digest, locking: Boolean): Rows? {
        val told = DigestColumns().also { key.writeTo(it) }
        val head = AttemptsTable.selectAll()
            .where { (AttemptsTable.secretDigest eq told.bytes()) and (AttemptsTable.pepperVersion eq told.version()) }
            .let { query -> if (locking) query.forUpdate() else query }
            .singleOrNull() ?: return null
        val id = head[AttemptsTable.id]
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
            id = id,
            purpose = words.purposeFrom(head[AttemptsTable.purpose]),
            startedAt = head[AttemptsTable.startedAt],
            verified = verified.map {
                Factor(
                    words.kindFrom(it[AttemptVerifiedFactorsTable.kind]),
                    it[AttemptVerifiedFactorsTable.subject],
                    it[AttemptVerifiedFactorsTable.verifiedAt],
                )
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
    private class Claiming(private val key: Digest, private val id: Uuid) : Attempt.Record {
        private val words = StoredWords()
        private val digest = DigestColumns().also { key.writeTo(it) }

        override fun started(purpose: Purpose, at: Instant) {
            TransactionManager.current().exec(
                CLAIM_ATTEMPT,
                listOf(
                    AttemptsTable.id.columnType to id,
                    AttemptsTable.purpose.columnType to words.of(purpose),
                    AttemptsTable.startedAt.columnType to at,
                    AttemptsTable.secretDigest.columnType to digest.bytes(),
                    AttemptsTable.pepperVersion.columnType to digest.version(),
                ),
            )
        }

        override fun identified(kind: FactorKind, subject: String, at: Instant) = Unit

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

        override fun identified(kind: FactorKind, subject: String, at: Instant) = append(kind, subject, at)

        override fun verified(kind: FactorKind, at: Instant) = append(kind, null, at)

        private fun append(kind: FactorKind, subject: String?, at: Instant) {
            verifiedAlready += 1
            AttemptVerifiedFactorsTable.insert {
                it[attemptId] = id
                it[position] = verifiedAlready
                it[AttemptVerifiedFactorsTable.kind] = words.of(kind)
                it[AttemptVerifiedFactorsTable.subject] = subject
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
        private val id: Uuid?,
        private val purpose: Purpose,
        private val startedAt: Instant,
        private val verified: List<Factor>,
        private val failures: List<Instant>,
    ) {
        constructor(attempt: Attempt) : this(Telling().also { attempt.writeTo(it) })

        private constructor(telling: Telling) : this(
            null,
            telling.purpose(),
            telling.startedAt(),
            telling.verifiedFactors(),
            telling.failureTimes(),
        )

        fun id(): Uuid = checkNotNull(id) { "Rows told by an attempt have no row of their own yet." }

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
            verified.forEach { it.replayInto(record) }
            failures.forEach { record.failed(it) }
        }

        /**
         * Only what these rows hold beyond [kept], which [continues] it.
         */
        fun replayBeyond(kept: Rows, record: Attempt.Record) {
            verified.drop(kept.verified.size).forEach { it.replayInto(record) }
            failures.drop(kept.failures.size).forEach { record.failed(it) }
        }
    }

    /**
     * A factor as a row holds it: its kind, whose account it proved if it identifies one, and when.
     */
    private data class Factor(val kind: FactorKind, val subject: String?, val at: Instant) {
        fun replayInto(record: Attempt.Record) {
            subject?.let { record.identified(kind, it, at) } ?: record.verified(kind, at)
        }

        // The subject names a person at Google; rows are compared, never printed.
        override fun toString(): String = "Factor(kind=$kind, at=$at)"
    }

    /**
     * Collects what an attempt tells, cutting each instant to the microsecond.
     */
    private class Telling : Attempt.Record {
        private val began = mutableListOf<Pair<Purpose, Instant>>()
        private val verified = mutableListOf<Factor>()
        private val failures = mutableListOf<Instant>()

        override fun started(purpose: Purpose, at: Instant) {
            began += purpose to at.toMicroseconds()
        }

        override fun identified(kind: FactorKind, subject: String, at: Instant) {
            verified += Factor(kind, subject, at.toMicroseconds())
        }

        override fun verified(kind: FactorKind, at: Instant) {
            verified += Factor(kind, null, at.toMicroseconds())
        }

        override fun failed(at: Instant) {
            failures += at.toMicroseconds()
        }

        fun purpose(): Purpose = began.single().first

        fun startedAt(): Instant = began.single().second

        fun verifiedFactors(): List<Factor> = verified.toList()

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
