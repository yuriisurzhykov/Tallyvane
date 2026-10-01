package tallyvane.authentication.domain

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * What one purpose demands of a sign-in, and the judge of how far an [Attempt] has got (ADR-078).
 *
 * The same policy answers for every account. What differs per account is passed in as its
 * [Enrollment], so "TOTP is required for this person because they turned it on" is the policy's
 * [Step.Necessity.WhenEnrolled] meeting that person's data, not a branch in code.
 *
 * Only [PolicyDraft.check] builds one, which is why the constructor is `internal`: every policy
 * that exists is one whose bounds were checked. The `require` below is not that check; it guards the
 * one assumption [progressOf] cannot work without, in case a later change inside this module forgets.
 *
 * @param purpose What the policy is for. The policy does not read it to judge an attempt; it is here
 * so that a policy kept in storage still says which purpose it answers for.
 * @param steps Every step that applies must be satisfied, in this order.
 * @param attemptLifetime How long an attempt stays open from the moment it started.
 * @param maxFailures Wrong answers after which the attempt is over.
 * @param firstDelay The pause after the first wrong answer; each further one doubles it (ADR-082).
 */
@ConsistentCopyVisibility
public data class SignInPolicy internal constructor(
    private val purpose: Purpose,
    private val steps: List<Step>,
    private val attemptLifetime: Duration,
    private val maxFailures: Int,
    private val firstDelay: Duration,
) {
    init {
        require(steps.any { it.identifiesTheAccount() }) {
            "A policy must start from a step every account can pass without setup, such as Google; " +
                "without one there is no way to learn whose account is signing in."
        }
    }

    /**
     * Where [attempt] stands for an account with this [enrollment] at [now].
     *
     * Asked afresh on every step of a sign-in rather than remembered, so the answer always reflects
     * the policy and the account as they are at that moment. Lifetime and the failure limit come
     * first: an attempt past either is over, whatever its factors say.
     */
    public fun progressOf(attempt: Attempt, enrollment: Enrollment, now: Instant): Progress = when {
        attempt.hasOutlived(attemptLifetime, now) -> Progress.Expired()
        attempt.hasFailedAtLeast(maxFailures) -> Progress.Exhausted()
        else -> openProgressOf(attempt, enrollment, now)
    }

    /**
     * Tells [record] what this policy is: its purpose, each step in order, then its limits.
     *
     * How storage keeps a policy it has been handed to make the next version of its purpose
     * (ADR-085); nothing here is for deciding anything, which is what [progressOf] is for.
     */
    public fun writeTo(record: Record) {
        record.purpose(purpose)
        steps.forEach { it.writeTo(record) }
        record.limits(attemptLifetime, maxFailures, firstDelay)
    }

    private fun openProgressOf(attempt: Attempt, enrollment: Enrollment, now: Instant): Progress {
        val (reachable, unreachable) =
            steps
                .filter { it.appliesTo(enrollment) }
                .partition { it.isReachableWith(enrollment) }
        val next = reachable.firstOrNull { !it.isSatisfiedBy(attempt) }

        return when {
            next != null -> next.requestFrom(attempt, firstDelay, now)
            unreachable.isEmpty() -> attempt.complete()
            else -> unreachable.first().restrict(attempt)
        }
    }

    /**
     * What a policy tells whoever keeps it: the purpose it is for, each step in the order they
     * apply, then the numbers.
     */
    public interface Record : Step.Record {
        /**
         * The policy answers for [purpose]. Always the first thing said.
         */
        public fun purpose(purpose: Purpose)

        /**
         * The attempt lives [attemptLifetime], ends after [maxFailures] wrong answers, and pauses
         * [firstDelay] after the first of them (ADR-082). Always the last thing said.
         */
        public fun limits(attemptLifetime: Duration, maxFailures: Int, firstDelay: Duration)
    }
}
