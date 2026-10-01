package tallyvane.authentication.infrastructure

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import tallyvane.authentication.application.port.PolicyVersions
import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.PolicyVersion
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.SignInPolicy
import tallyvane.authentication.domain.Step
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * [PolicyVersions] over the `authentication` schema in Postgres, through Exposed's DSL (ADR-049).
 *
 * Runs inside the caller's transaction and never opens one (ADR-052). A version is told to
 * [PolicyVersion.writeTo] a listener that fills rows, and brought back by [PolicyVersion.restore]
 * from a replay of those rows (ADR-085), so reading a version re-checks it against the bounds the
 * code sets today.
 *
 * Nothing here updates or deletes: versions and activations are only inserted, and the schema's
 * triggers refuse anything else.
 *
 * ### Two administrators adding at once
 *
 * The next number is "the highest kept plus one", which two transactions would compute alike. [add]
 * therefore takes a transaction-scoped advisory lock named after the purpose first, so the second
 * waits for the first to commit and then counts past it. The primary key on `(purpose, number)` is
 * what would still refuse a duplicate if the lock were ever left out.
 */
internal class PostgresPolicyVersions : PolicyVersions {
    private val words = StoredWords()

    override fun active(purpose: Purpose): PolicyVersion? {
        val purposeWord = words.of(purpose)
        val latest = PolicyActivationsTable.selectAll()
            .where { PolicyActivationsTable.purpose eq purposeWord }
            .orderBy(PolicyActivationsTable.id, SortOrder.DESC)
            .limit(1)
            .singleOrNull() ?: return null
        return versionOf(purposeWord, latest[PolicyActivationsTable.number])
    }

    override fun add(policy: SignInPolicy, at: Instant): PolicyVersion {
        val purposeWord = words.of(PurposeHeard().of(policy))
        lockCountingOf(purposeWord)
        val highest = PolicyVersionsTable.select(PolicyVersionsTable.number.max())
            .where { PolicyVersionsTable.purpose eq purposeWord }
            .single()[PolicyVersionsTable.number.max()] ?: 0
        PolicyVersion(highest + 1, policy).writeTo(Inserting(at))
        return versionOf(purposeWord, highest + 1)
    }

    override fun activate(version: PolicyVersion, at: Instant) {
        val heard = VersionHeard().also { version.writeTo(it) }
        val purposeWord = words.of(heard.purpose())
        val kept = PolicyVersionsTable.selectAll()
            .where { (PolicyVersionsTable.purpose eq purposeWord) and (PolicyVersionsTable.number eq heard.number()) }
            .count()
        check(kept == 1L) {
            "Version ${heard.number()} of $purposeWord is not kept, so it cannot be put in force. " +
                "Only a version obtained from PolicyVersions.add or active can be activated."
        }
        val keptHeard = VersionHeard().also { versionOf(purposeWord, heard.number()).writeTo(it) }
        check(keptHeard.told() == heard.told()) {
            "Version ${heard.number()} of $purposeWord is kept, but the version given says something else " +
                "than the one kept. A version put in force must be the one that PolicyVersions.add or " +
                "active handed out, not one built to look like it."
        }
        PolicyActivationsTable.insert {
            it[purpose] = purposeWord
            it[number] = heard.number()
            it[activatedAt] = at
        }
    }

    override fun toString(): String = "PostgresPolicyVersions(schema=authentication)"

    /**
     * Makes concurrent adds for one purpose take turns, until the transaction ends.
     */
    private fun lockCountingOf(purposeWord: String) {
        TransactionManager.current().exec(
            "select pg_advisory_xact_lock(hashtext(?))",
            listOf(TextColumnType() to "authentication.policy_versions.$purposeWord"),
        ) { }
    }

    private fun versionOf(purposeWord: String, number: Int): PolicyVersion {
        val head = PolicyVersionsTable.selectAll()
            .where { (PolicyVersionsTable.purpose eq purposeWord) and (PolicyVersionsTable.number eq number) }
            .single()
        val steps = PolicyVersionStepsTable.selectAll()
            .where { (PolicyVersionStepsTable.purpose eq purposeWord) and (PolicyVersionStepsTable.number eq number) }
            .orderBy(PolicyVersionStepsTable.position, SortOrder.ASC)
            .toList()
        val kinds = PolicyVersionStepKindsTable.selectAll()
            .where {
                (PolicyVersionStepKindsTable.purpose eq purposeWord) and (PolicyVersionStepKindsTable.number eq number)
            }
            .groupBy({
                it[PolicyVersionStepKindsTable.position]
            }, { words.kindFrom(it[PolicyVersionStepKindsTable.kind]) })
        check(steps.map { it[PolicyVersionStepsTable.position] } == (1..steps.size).toList()) {
            "The steps of version $number of $purposeWord are not numbered 1, 2, 3 without a gap. " +
                "Versions are never edited, so restore authentication.policy_version_steps from a backup."
        }
        return PolicyVersion.restore { record ->
            record.number(number)
            record.purpose(words.purposeFrom(head[PolicyVersionsTable.purpose]))
            steps.forEach { step ->
                record.step(
                    kinds[step[PolicyVersionStepsTable.position]].orEmpty().toSet(),
                    words.necessityFrom(step[PolicyVersionStepsTable.necessity]),
                )
            }
            record.limits(
                head[PolicyVersionsTable.attemptLifetimeMillis].milliseconds,
                head[PolicyVersionsTable.maxFailures],
                head[PolicyVersionsTable.firstDelayMillis].milliseconds,
            )
        }
    }

    /**
     * Inserts a version from what it tells. The steps come before the limits, and the rows of the
     * steps point at the row of the version, so they are held until the limits arrive: the last thing
     * a version says, which is when everything the version row needs is known.
     */
    private class Inserting(private val at: Instant) : PolicyVersion.Record {
        private val words = StoredWords()
        private val numbers = mutableListOf<Int>()
        private val purposes = mutableListOf<Purpose>()
        private val steps = mutableListOf<Pair<Set<FactorKind>, Step.Necessity>>()

        override fun number(number: Int) {
            numbers += number
        }

        override fun purpose(purpose: Purpose) {
            purposes += purpose
        }

        override fun step(accepts: Set<FactorKind>, necessity: Step.Necessity) {
            steps += accepts to necessity
        }

        override fun limits(attemptLifetime: Duration, maxFailures: Int, firstDelay: Duration) {
            val purposeWord = words.of(purposes.single())
            val versionNumber = numbers.single()
            PolicyVersionsTable.insert {
                it[purpose] = purposeWord
                it[number] = versionNumber
                it[attemptLifetimeMillis] = attemptLifetime.inWholeMilliseconds
                it[PolicyVersionsTable.maxFailures] = maxFailures
                it[firstDelayMillis] = firstDelay.inWholeMilliseconds
                it[createdAt] = at
            }
            steps.forEachIndexed { index, (accepts, necessity) ->
                val position = index + 1
                PolicyVersionStepsTable.insert {
                    it[purpose] = purposeWord
                    it[number] = versionNumber
                    it[PolicyVersionStepsTable.position] = position
                    it[PolicyVersionStepsTable.necessity] = words.of(necessity)
                }
                accepts.forEach { kind ->
                    PolicyVersionStepKindsTable.insert {
                        it[purpose] = purposeWord
                        it[number] = versionNumber
                        it[PolicyVersionStepKindsTable.position] = position
                        it[PolicyVersionStepKindsTable.kind] = words.of(kind)
                    }
                }
            }
        }
    }

    /**
     * Hears which purpose a policy is for, which it tells first.
     */
    private class PurposeHeard : SignInPolicy.Record {
        private val heard = mutableListOf<Purpose>()

        fun of(policy: SignInPolicy): Purpose {
            policy.writeTo(this)
            return heard.single()
        }

        override fun purpose(purpose: Purpose) {
            heard += purpose
        }

        override fun step(accepts: Set<FactorKind>, necessity: Step.Necessity) = Unit

        override fun limits(attemptLifetime: Duration, maxFailures: Int, firstDelay: Duration) = Unit
    }

    /**
     * Hears everything a version tells, so that activating can name it (purpose and number) and can
     * tell whether it is the one that is kept: two versions are the same when they say the same words.
     */
    private class VersionHeard : PolicyVersion.Record {
        private val purposes = mutableListOf<Purpose>()
        private val numbers = mutableListOf<Int>()
        private val lines = mutableListOf<String>()

        fun purpose(): Purpose = purposes.single()

        fun number(): Int = numbers.single()

        fun told(): List<String> = lines.toList()

        override fun number(number: Int) {
            numbers += number
            lines += "number $number"
        }

        override fun purpose(purpose: Purpose) {
            purposes += purpose
            lines += "purpose $purpose"
        }

        override fun step(accepts: Set<FactorKind>, necessity: Step.Necessity) {
            lines += "step ${accepts.sortedBy { it.ordinal }} $necessity"
        }

        override fun limits(attemptLifetime: Duration, maxFailures: Int, firstDelay: Duration) {
            lines += "limits $attemptLifetime $maxFailures $firstDelay"
        }
    }
}
