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
     * Time only moves forward in an attempt, which is what lets whatever keeps it bring it back. A
     * factor stamped earlier than the start or than a factor already recorded is recorded at that
     * later moment instead (slice 3, fork 5): the clocks of two servers, or one clock stepping back,
     * may disagree by milliseconds, and a person must not be refused because of it. A pause shifts by
     * the same milliseconds, which changes nothing anyone can notice.
     *
     * @throws IllegalArgumentException for a factor that names a different person than one already
     * recorded: one attempt is one person's sign-in, and a second Google account half-way through is
     * not a step of it.
     */
    public fun withVerified(factor: VerifiedFactor): Attempt =
        recording(factor.notBefore(maxOf(startedAt, verified.maxOfOrNull { it.at } ?: startedAt)))

    /**
     * This attempt with one more wrong answer, given [at], moved forward like a factor in
     * [withVerified] when the clock that stamped it was behind.
     */
    public fun withFailure(at: Instant): Attempt =
        failing(maxOf(at, startedAt, failures.lastOrNull() ?: startedAt))

    /**
     * [factor] recorded exactly when it says, which only a restore may ask for: a stored history out
     * of order was not written by this class, and moving it forward would hide that.
     */
    private fun recording(factor: VerifiedFactor): Attempt {
        require(factor.at >= startedAt && verified.all { it.at <= factor.at }) {
            "A factor verified at ${factor.at} comes before the attempt began at $startedAt or before a " +
                "factor already verified."
        }
        require(verified.none { it.disagreesWith(factor) }) {
            "This attempt already belongs to one person and cannot record a factor naming another. " +
                "Start a new attempt for the second account."
        }
        return Attempt(purpose, startedAt, verified + factor, failures)
    }

    /**
     * A wrong answer recorded exactly at [at], for the reason [recording] gives.
     */
    private fun failing(at: Instant): Attempt {
        require(at >= startedAt && failures.all { it <= at }) {
            "A wrong answer at $at comes before the attempt began at $startedAt or before an earlier answer."
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
        verified.forEach { it.writeTo(record) }
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
    internal fun complete(): Progress = verifiedAs { kinds, at, subject -> Progress.Complete(kinds, at, subject) }

    /**
     * Everything reachable is verified; the person may only set up one of [toSetUp].
     */
    internal fun restrictedTo(toSetUp: Set<FactorKind>): Progress =
        verifiedAs { kinds, at, subject -> Progress.Restricted(kinds, at, subject, toSetUp) }

    /**
     * The kinds verified so far, the time of the last of them and whose account they proved, which an
     * outcome built from them carries.
     *
     * Never called before the account is identified: a policy only reaches Complete or Restricted
     * after the step that identifies it, so [verified] holds a factor that names somebody.
     */
    private fun verifiedAs(outcome: (Set<FactorKind>, Instant, String) -> Progress): Progress =
        outcome(
            verified.mapTo(mutableSetOf()) { it.kind },
            verified.maxOf { it.at },
            checkNotNull(verified.firstNotNullOfOrNull { it.subject }) {
                "A policy let an attempt through before any factor named whose it is; " +
                    "SignInPolicy requires a step that identifies the account."
            },
        )

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
         * A factor of this [kind], one that identifies the account, vouched at [at] for [subject].
         */
        public fun identified(kind: FactorKind, subject: String, at: Instant)

        /**
         * A factor of this [kind], one that confirms an account already identified, was verified at
         * [at].
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
     * Builds the attempt as the words arrive, through the same checks [withVerified] and
     * [withFailure] apply after moving a late stamp forward, so a restored attempt is one that could
     * have been lived and no attempt can grow into a history that this refuses. Unlike those two, it
     * moves nothing: a stored history out of order was written by something else. The list holds at
     * most one attempt and is the only mutable thing here: a `var` would do the same, but
     * `domain` has none.
     */
    private class Restoration : Record {
        private val growing = mutableListOf<Attempt>()

        override fun started(purpose: Purpose, at: Instant) {
            check(growing.isEmpty()) { refused("it starts twice") }
            growing += Attempt(purpose, at)
        }

        override fun identified(kind: FactorKind, subject: String, at: Instant) {
            val so = current("a factor is verified before it starts")
            growing[0] = so.grownBy("a factor is out of order, of the wrong kind, or names a second person") {
                recording(VerifiedFactor.identifying(kind, subject, at))
            }
        }

        override fun verified(kind: FactorKind, at: Instant) {
            val so = current("a factor is verified before it starts")
            growing[0] = so.grownBy("a factor is out of order or of a kind that must say whose it is") {
                recording(VerifiedFactor.confirming(kind, at))
            }
        }

        override fun failed(at: Instant) {
            val so = current("a wrong answer comes before it starts")
            growing[0] = so.grownBy("a wrong answer comes before the attempt started or before an earlier one") {
                failing(at)
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
