package tallyvane.authentication.domain

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * One thing a sign-in must show: any one of the factor kinds it accepts.
 *
 * A policy is a list of steps, and every step that applies must be satisfied — "Google, and then
 * TOTP or a recovery code" is two steps, the second accepting two kinds. That shape (all of these,
 * each by any of those) covers every policy ADR-078 starts with, and it keeps the order of a
 * sign-in a property of the data: nothing in the code says "after Google comes TOTP".
 *
 * Its questions are `internal`: only [SignInPolicy] asks them. Outside the module a step is a value
 * to build a policy from, and nothing more.
 *
 * @param accepts The kinds that satisfy this step. An empty set is representable, because an
 * administrator can submit one, but [PolicyDraft.check] rejects it: a step nothing can satisfy would
 * lock every account out.
 * @param necessity Whether the step applies to every account or only to those that enabled one of
 * its factors.
 */
public data class Step(private val accepts: Set<FactorKind>, private val necessity: Necessity) {
    /**
     * Before the account is known, [enrollment] is [Enrollment.Unknown] and only [Necessity.Always]
     * steps apply: nothing about an account can be demanded before we know whose it is.
     */
    internal fun appliesTo(enrollment: Enrollment): Boolean = when (necessity) {
        Necessity.Always -> true
        Necessity.WhenEnrolled -> accepts.any { enrollment.includes(it) }
    }

    internal fun isEmpty(): Boolean = accepts.isEmpty()

    /**
     * Whether every account that set up one of this step's kinds must pass it with one: only
     * factors that need setting up are accepted, so Google cannot stand in for them.
     */
    internal fun demandsSecondFactorFromTheEnrolled(): Boolean =
        accepts.isNotEmpty() && accepts.none { it.isAvailableTo(Enrollment.Unknown) }

    /**
     * Whether every account, set up or not, must pass this step with a factor it set up itself:
     * the shape of a mandatory second factor.
     */
    internal fun demandsSecondFactorFromEveryone(): Boolean =
        necessity == Necessity.Always && demandsSecondFactorFromTheEnrolled()

    /**
     * Tells [record] which kinds satisfy this step and to whom it applies.
     */
    internal fun writeTo(record: Record) {
        record.step(accepts, necessity)
    }

    /**
     * This step with a set of its own, detached from whatever set the caller built it from.
     */
    internal fun detached(): Step = Step(accepts.toSet(), necessity)

    internal fun isSatisfiedBy(attempt: Attempt): Boolean = attempt.hasVerifiedOneOf(accepts)

    /**
     * False only when every accepted kind needs setting up first and none of them is: an
     * administrator without TOTP facing a step that always demands it (ADR-078).
     */
    internal fun isReachableWith(enrollment: Enrollment): Boolean = accepts.any { it.isAvailableTo(enrollment) }

    /**
     * Whether this step tells us whose account is signing in: it applies to everyone and can be
     * passed without setting anything up.
     */
    internal fun identifiesTheAccount(): Boolean = necessity == Necessity.Always && isReachableWith(Enrollment.Unknown)

    /**
     * This step is next: [attempt] is asked for any of its kinds that an account with this [enrollment]
     * can verify, now or after its pause. A kind the account cannot pass, such as a retired TOTP seed,
     * is not offered, so nobody is invited to a wrong answer that would count against them.
     */
    internal fun requestFrom(attempt: Attempt, enrollment: Enrollment, firstDelay: Duration, now: Instant): Progress =
        attempt.awaiting(accepts.filterTo(mutableSetOf()) { it.isAvailableTo(enrollment) }, firstDelay, now)

    /**
     * This step cannot be reached yet: [attempt] is let in only to set up one of its kinds.
     */
    internal fun restrict(attempt: Attempt): Progress = attempt.concluded(accepts)

    /**
     * When a step applies.
     */
    public enum class Necessity {
        /**
         * To every account: the floor the policy sets for everyone.
         */
        Always,

        /**
         * Only to an account that enabled one of the step's factors: the user raising their own bar.
         */
        WhenEnrolled,
    }

    /**
     * What a step tells whoever keeps the policy it belongs to. A step is rebuilt from the same two
     * values by its public constructor, so nothing here needs a matching `restore`.
     */
    public interface Record {
        /**
         * The next step, in the order they apply, is satisfied by any of [accepts] and applies as
         * [necessity] says.
         */
        public fun step(accepts: Set<FactorKind>, necessity: Necessity)
    }
}
