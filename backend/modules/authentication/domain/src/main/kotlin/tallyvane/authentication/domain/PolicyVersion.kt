package tallyvane.authentication.domain

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * One numbered version of the policy for a purpose (ADR-078).
 *
 * A change never edits a policy; it makes the next version, and one version at a time is in force.
 * The number is what a rollback names. A version judges an attempt exactly as the policy it holds
 * does, so the code that finds the version in force asks it, and never reaches into it.
 *
 * Its state leaves through [writeTo] and comes back through [restore] (ADR-085). It is not a
 * `data class`, which would publish a `copy` that makes a version with a number nobody assigned.
 *
 * @param number Counts from 1 within one purpose.
 * @param policy The checked policy this version holds, which is why nothing here re-checks bounds.
 */
public class PolicyVersion(private val number: Int, private val policy: SignInPolicy) {
    init {
        require(number >= 1) {
            "A policy version is numbered from 1, but was given $number. " +
                "Storage assigns the number when it keeps a version; do not make one up."
        }
    }

    /**
     * Where [attempt] stands under this version for an account with this [enrollment] at [now].
     */
    public fun progressOf(attempt: Attempt, enrollment: Enrollment, now: Instant): Progress =
        policy.progressOf(attempt, enrollment, now)

    override fun toString(): String = "PolicyVersion(number=$number, policy=$policy)"

    /**
     * Tells [record] the number, then everything the policy says about itself.
     */
    public fun writeTo(record: Record) {
        record.number(number)
        policy.writeTo(record)
    }

    /**
     * What a version tells whoever keeps it, and what that keeper tells [restore] to bring it back.
     *
     * The number first, then the policy's own words: purpose, steps, limits.
     */
    public interface Record : SignInPolicy.Record {
        /**
         * The version is numbered [number]. Always the first thing said.
         */
        public fun number(number: Int)
    }

    public companion object {
        /**
         * The version a keeper [replay]s into the [Record] it is handed, in the order [writeTo] says
         * things.
         *
         * The numbers and steps come back as they were; the policy is checked against the bounds the
         * code has *today*. A version saved under wider bounds and kept after they narrowed is
         * refused here, loudly, rather than judged under numbers the code no longer allows.
         *
         * @throws IllegalStateException for a replay that is incomplete, or describes a policy that
         * breaks a bound.
         */
        public fun restore(replay: (Record) -> Unit): PolicyVersion = Restoration().also(replay).version()
    }

    /**
     * Collects a replay, then lets a [PolicyDraft] decide whether it may be a policy at all.
     *
     * A draft accepts any values, so assembling one cannot fail; [PolicyDraft.check] is where the
     * bounds speak, the same as when an administrator proposed these values.
     */
    private class Restoration : Record {
        private val numbers = mutableListOf<Int>()
        private val purposes = mutableListOf<Purpose>()
        private val steps = mutableListOf<Step>()
        private val limits = mutableListOf<Limits>()

        override fun number(number: Int) {
            numbers += number
        }

        override fun purpose(purpose: Purpose) {
            purposes += purpose
        }

        override fun step(accepts: Set<FactorKind>, necessity: Step.Necessity) {
            steps += Step(accepts, necessity)
        }

        override fun limits(attemptLifetime: Duration, maxFailures: Int, firstDelay: Duration) {
            limits += Limits(attemptLifetime, maxFailures, firstDelay)
        }

        fun version(): PolicyVersion {
            val number = checkNotNull(numbers.singleOrNull()) { refused("it has no single number") }
            val purpose = checkNotNull(purposes.singleOrNull()) { refused("it has no single purpose") }
            val limit = checkNotNull(limits.singleOrNull()) { refused("it has no single set of limits") }
            return limit.draftFor(purpose, steps).check().reportTo(Judgement(number))
        }

        private fun refused(reason: String): String =
            "A stored policy version cannot be restored: $reason. PolicyVersion.writeTo always says all " +
                "of it, so the stored rows were changed by something else. Versions are never edited; " +
                "restore the rows from a backup or add a new version and activate that one."

        /**
         * A [PolicyDraft] waiting for its purpose and steps, which a replay delivers separately.
         */
        private class Limits(
            private val attemptLifetime: Duration,
            private val maxFailures: Int,
            private val firstDelay: Duration,
        ) {
            fun draftFor(purpose: Purpose, steps: List<Step>): PolicyDraft =
                PolicyDraft(purpose, steps.toList(), attemptLifetime, maxFailures, firstDelay)
        }

        private class Judgement(private val number: Int) : DraftCheck.Report<PolicyVersion> {
            override fun passed(policy: SignInPolicy): PolicyVersion = PolicyVersion(number, policy)

            override fun rejected(violations: List<Violation>): PolicyVersion = error(
                "Stored policy version $number breaks the bounds the code sets today: $violations. " +
                    "The bounds were narrowed after it was saved. Activate another version, or " +
                    "add one within the bounds and activate that.",
            )
        }
    }
}
