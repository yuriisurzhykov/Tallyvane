package tallyvane.authentication.domain

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * One sign-in in progress: the owner's "sign-in token" (ADR-078).
 *
 * It records what happened — which factors were verified and when the wrong answers came — and
 * answers questions about that record. It does not decide whether the record is enough;
 * [SignInPolicy] does, against whichever policy version is active at the moment of asking. So a
 * policy tightened while someone is half-way through applies to their next step.
 *
 * Once the policy has decided, the attempt is the one that states the outcome as a [Progress],
 * because only it holds the facts the outcome carries. Nothing asks it for its lists.
 *
 * Not a `data class`, on purpose: its generated `copy()` would be public, and
 * `attempt.copy(failures = emptyList())` would wipe the record that limits guessing. The only ways
 * to change an attempt are [withVerified] and [withFailure], and both only add.
 */
public class Attempt private constructor(
    private val startedAt: Instant,
    private val verified: List<VerifiedFactor>,
    private val failures: List<Instant>,
) {
    /**
     * A new attempt with nothing verified and no wrong answers.
     */
    public constructor(startedAt: Instant) : this(startedAt, emptyList(), emptyList())

    /**
     * This attempt with [factor] recorded as verified.
     */
    public fun withVerified(factor: VerifiedFactor): Attempt = Attempt(startedAt, verified + factor, failures)

    /**
     * This attempt with one more wrong answer, given [at].
     */
    public fun withFailure(at: Instant): Attempt = Attempt(startedAt, verified, failures + at)

    internal fun hasOutlived(lifetime: Duration, now: Instant): Boolean = now >= startedAt + lifetime

    internal fun hasFailedAtLeast(times: Int): Boolean = failures.size >= times

    internal fun hasVerifiedOneOf(kinds: Set<FactorKind>): Boolean = verified.any { it.kind in kinds }

    /**
     * The next factor is wanted as any one of [accepted]: now, or after the pause the wrong answers
     * earned. That pause is [firstDelay] after the first wrong answer and doubles with each further
     * one (ADR-082).
     */
    internal fun awaiting(accepted: Set<FactorKind>, firstDelay: Duration, now: Instant): Progress {
        val lastFailure = failures.lastOrNull() ?: return Progress.Awaiting(accepted)
        val resumesAt = lastFailure + firstDelay * (1 shl (failures.size - 1))
        return if (now < resumesAt) Progress.Paused(accepted, resumesAt) else Progress.Awaiting(accepted)
    }

    /**
     * Everything the policy asks for is verified.
     */
    internal fun complete(): Progress = Progress.Complete(verifiedKinds(), authenticatedAt())

    /**
     * Everything reachable is verified; the person may only set up one of [toSetUp].
     */
    internal fun restrictedTo(toSetUp: Set<FactorKind>): Progress =
        Progress.Restricted(verifiedKinds(), authenticatedAt(), toSetUp)

    private fun verifiedKinds(): Set<FactorKind> = verified.mapTo(mutableSetOf()) { it.kind }

    /**
     * Never called before a factor is verified: a policy only reaches Complete or Restricted after
     * the step that identifies the account, so [verified] is not empty here.
     */
    private fun authenticatedAt(): Instant = verified.maxOf { it.at }

    override fun toString(): String = "Attempt(startedAt=$startedAt, verified=$verified, failures=${failures.size})"
}
