package tallyvane.authentication.domain

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * One sign-in in progress: the owner's "sign-in token" (ADR-078).
 *
 * It records why the person is signing in and what happened — which factors were verified and when
 * the wrong answers came — and
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
 *
 * Its state leaves through [writeTo] and comes back through [restore] (ADR-085), and through nothing
 * else: no list is ever handed out, even to the storage that has to keep it.
 */
public class Attempt private constructor(
    private val purpose: Purpose,
    private val startedAt: Instant,
    private val verified: List<VerifiedFactor>,
    private val failures: List<Instant>,
) {
    /**
     * A new attempt for [purpose] with nothing verified and no wrong answers.
     */
    public constructor(purpose: Purpose, startedAt: Instant) : this(purpose, startedAt, emptyList(), emptyList())

    /**
     * This attempt with [factor] recorded as verified.
     *
     * @throws IllegalArgumentException for a factor verified before the attempt began or before one
     * already recorded. Time only moves forward in an attempt, which is what lets whatever keeps it
     * bring it back; a change applied to a reloaded attempt takes its time from the clock again.
     */
    public fun withVerified(factor: VerifiedFactor): Attempt {
        require(factor.at >= startedAt && verified.all { it.at <= factor.at }) {
            "A factor verified at ${factor.at} comes before the attempt began at $startedAt or before a " +
                "factor already verified. Read the clock again when applying a change to a reloaded " +
                "attempt; the time of the request that lost the race is already behind it."
        }
        return Attempt(purpose, startedAt, verified + factor, failures)
    }

    /**
     * This attempt with one more wrong answer, given [at].
     *
     * @throws IllegalArgumentException for an answer given before the attempt began or before an
     * earlier wrong answer, for the reason [withVerified] gives.
     */
    public fun withFailure(at: Instant): Attempt {
        require(at >= startedAt && failures.all { it <= at }) {
            "A wrong answer at $at comes before the attempt began at $startedAt or before an " +
                "earlier answer. Read the clock again when applying a change to a reloaded attempt; " +
                "the time of the request that lost the race is already behind it."
        }
        return Attempt(purpose, startedAt, verified, failures + at)
    }

    /**
     * Tells [record] everything this attempt holds: how it started, then each verified factor in the
     * order they came, then each wrong answer in the order they came.
     *
     * The attempt decides what to say and in which order; the [record] only listens. That is how
     * storage keeps an attempt without being able to read one.
     */
    public fun writeTo(record: Record) {
        record.started(purpose, startedAt)
        verified.forEach { record.verified(it.kind, it.at) }
        failures.forEach { record.failed(it) }
    }

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
    internal fun complete(): Progress = verifiedAs { kinds, at -> Progress.Complete(kinds, at) }

    /**
     * Everything reachable is verified; the person may only set up one of [toSetUp].
     */
    internal fun restrictedTo(toSetUp: Set<FactorKind>): Progress =
        verifiedAs { kinds, at -> Progress.Restricted(kinds, at, toSetUp) }

    /**
     * The kinds verified so far and the time of the last of them, which an outcome built from them
     * carries.
     *
     * Never called before a factor is verified: a policy only reaches Complete or Restricted after
     * the step that identifies the account, so [verified] is not empty here.
     */
    private fun verifiedAs(outcome: (Set<FactorKind>, Instant) -> Progress): Progress =
        outcome(verified.mapTo(mutableSetOf()) { it.kind }, verified.maxOf { it.at })

    override fun toString(): String =
        "Attempt(purpose=$purpose, startedAt=$startedAt, verified=$verified, failures=${failures.size})"

    /**
     * What an attempt tells whoever keeps it, and what that keeper tells [restore] to bring it back.
     *
     * The same three words both ways, so that what can be written is exactly what can be restored.
     */
    public interface Record {
        /**
         * The attempt began for [purpose] at [at]. Always the first thing said.
         */
        public fun started(purpose: Purpose, at: Instant)

        /**
         * A factor of this [kind] was verified at [at].
         */
        public fun verified(kind: FactorKind, at: Instant)

        /**
         * A wrong answer was given at [at].
         */
        public fun failed(at: Instant)
    }

    public companion object {
        /**
         * The attempt a keeper [replay]s into the [Record] it is handed, in the order [writeTo] says
         * things.
         *
         * Refuses a replay that no attempt could have produced: nothing before the start, a start
         * twice, a factor or a wrong answer from before the attempt began, answers out of order. Such
         * a replay is a row edited by hand or written by something other than [writeTo], and
         * continuing with it would let someone's sign-in be judged on a history that never happened.
         *
         * @throws IllegalStateException for a replay no attempt could have told.
         */
        public fun restore(replay: (Record) -> Unit): Attempt = Restoration().also(replay).attempt()
    }

    /**
     * Collects a replay and checks that it is a history an attempt could have.
     *
     * Builds the attempt as the words arrive, through the same [withVerified] and [withFailure] any
     * attempt grows by, so a restored attempt is one that could have been lived and no attempt can
     * grow into a history that this refuses. The list holds at
     * most one attempt and is the only mutable thing here: a `var` would do the same, but
     * `domain` has none.
     */
    private class Restoration : Record {
        private val growing = mutableListOf<Attempt>()

        override fun started(purpose: Purpose, at: Instant) {
            check(growing.isEmpty()) { refused("it starts twice") }
            growing += Attempt(purpose, at)
        }

        override fun verified(kind: FactorKind, at: Instant) {
            val so = current("a factor is verified before it starts")
            growing[0] = so.grownBy("a factor is verified before the attempt started or before an earlier one") {
                withVerified(VerifiedFactor(kind, at))
            }
        }

        override fun failed(at: Instant) {
            val so = current("a wrong answer comes before it starts")
            growing[0] = so.grownBy("a wrong answer comes before the attempt started or before an earlier one") {
                withFailure(at)
            }
        }

        fun attempt(): Attempt = current("it never starts")

        private fun current(unlessBecause: String): Attempt = checkNotNull(growing.singleOrNull()) {
            refused(unlessBecause)
        }

        private fun Attempt.grownBy(because: String, growth: Attempt.() -> Attempt): Attempt = try {
            growth()
        } catch (refusal: IllegalArgumentException) {
            throw IllegalStateException(refused(because), refusal)
        }

        private fun refused(reason: String): String =
            "A stored attempt cannot be restored: $reason. Attempt.writeTo never says that, so the " +
                "stored rows were changed by something else. Do not repair them by hand; the " +
                "attempt is short-lived, so delete it and let the person sign in again."
    }
}
