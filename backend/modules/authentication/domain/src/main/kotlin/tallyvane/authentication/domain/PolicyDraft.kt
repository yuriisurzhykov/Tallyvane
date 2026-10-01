package tallyvane.authentication.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A policy as someone proposed it, before anyone has checked it may exist (ADR-078).
 *
 * The code sets the bounds and the policy sets the values: an administrator chooses how long an
 * attempt lives, but only between [ATTEMPT_LIFETIME]'s ends. [check] is the one way from a draft to
 * a [SignInPolicy], so a policy outside the bounds is not merely refused at the door; it cannot be
 * built at all.
 *
 * The policy [check] returns owns copies of the steps and their sets. A caller that keeps the list
 * it submitted, say a form model, and changes it afterwards changes its draft, never the policy
 * that passed.
 *
 * ```
 * PolicyDraft(
 *     purpose = Purpose.Login,
 *     steps = listOf(
 *         Step(setOf(FactorKind.Google), Step.Necessity.Always),
 *         Step(setOf(FactorKind.Totp, FactorKind.RecoveryCode), Step.Necessity.WhenEnrolled),
 *     ),
 *     attemptLifetime = 5.minutes,
 *     maxFailures = 5,
 *     firstDelay = 1.seconds,
 * ).check()
 * ```
 *
 * @param purpose What the policy is for; it sets the floor the steps must keep.
 * @param steps Every step that applies must be satisfied, in this order.
 * @param attemptLifetime How long an attempt stays open from the moment it started.
 * @param maxFailures Wrong answers after which the attempt is over.
 * @param firstDelay The pause after the first wrong answer; each further one doubles it (ADR-082).
 */
public class PolicyDraft(
    private val purpose: Purpose,
    private val steps: List<Step>,
    private val attemptLifetime: Duration,
    private val maxFailures: Int,
    private val firstDelay: Duration,
) {
    /**
     * The policy this draft describes, or every bound it breaks.
     */
    public fun check(): DraftCheck {
        val violations = boundViolations() + shapeViolations()
        return if (violations.isEmpty()) {
            DraftCheck.Passed(
                SignInPolicy(purpose, steps.map { it.detached() }, attemptLifetime, maxFailures, firstDelay),
            )
        } else {
            DraftCheck.Rejected(violations)
        }
    }

    private fun boundViolations(): List<Violation> = listOfNotNull(
        Violation.AttemptLifetimeOutOfBounds(attemptLifetime, ATTEMPT_LIFETIME)
            .takeUnless { attemptLifetime in ATTEMPT_LIFETIME },
        Violation.MaxFailuresOutOfBounds(maxFailures, MAX_FAILURES)
            .takeUnless { maxFailures in MAX_FAILURES },
        Violation.FirstDelayOutOfBounds(firstDelay, FIRST_DELAY)
            .takeUnless { firstDelay in FIRST_DELAY },
    )

    private fun shapeViolations(): List<Violation> =
        steps.withIndex().filter { it.value.isEmpty() }.map { Violation.EmptyStep(position = it.index + 1) } +
            listOfNotNull(
                Violation.NothingIdentifiesTheAccount().takeUnless { steps.any { it.identifiesTheAccount() } },
                Violation.BelowTheFloor(purpose).takeUnless { purpose.isKeptBy(steps) },
            )

    override fun toString(): String = "PolicyDraft(purpose=$purpose, steps=$steps, attemptLifetime=$attemptLifetime, " +
        "maxFailures=$maxFailures, firstDelay=$firstDelay)"

    private companion object {
        /**
         * Long enough to fetch a phone and read a code; short enough that a stolen attempt
         * identifier is soon worthless.
         */
        val ATTEMPT_LIFETIME: ClosedRange<Duration> = 1.minutes..15.minutes

        /**
         * Enough to forgive a few typos, too few to guess six digits.
         */
        val MAX_FAILURES: IntRange = 3..10

        /**
         * The pause doubles from here, so even the upper end reaches minutes within a few failures.
         */
        val FIRST_DELAY: ClosedRange<Duration> = 1.seconds..10.seconds
    }
}
