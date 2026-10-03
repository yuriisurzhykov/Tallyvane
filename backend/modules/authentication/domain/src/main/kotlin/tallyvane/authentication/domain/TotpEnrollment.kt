package tallyvane.authentication.domain

import tallyvane.platform.kernel.Secret
import java.net.URLEncoder
import kotlin.time.Instant

/**
 * One account's TOTP: the seed an authenticator app shares with us, how far it has got, and what the
 * codes it makes are worth (ADR-082, ADR-093).
 *
 * It is [Standing.Pending] from the moment it is begun until the person types the first code, and
 * counts for nothing then: a seed nobody has proved they can read protects no one. [Standing.Active]
 * is the only standing in which a code is a factor. [Standing.Retired] is what an active enrolment
 * becomes when a recovery code is spent in its place: the phone may be lost, so the seed must stop
 * working, and it stays retired until the person begins again.
 *
 * A code is accepted once: the step it belongs to is remembered, and from then on only strictly later
 * steps are accepted, so a code that was seen over a shoulder, or typed twice by a double click, is no
 * use the second time.
 *
 * The seed is held as a [Secret] in the alphabet an authenticator reads, and leaves through [writeTo]
 * and [provision] only. Not a `data class`, whose `copy` would make an enrolment with a standing nobody
 * reached. It knows nothing of an account, a cipher or a database: who it belongs to is the key it is
 * kept under, and sealing the seed is the keeper's business (ADR-085).
 */
public class TotpEnrollment private constructor(
    private val seed: Secret,
    private val standing: Standing,
    private val lastAcceptedStep: Long?,
) {
    /**
     * Whether this enrolment's codes are a factor: only an active one.
     */
    public fun isActive(): Boolean = standing == Standing.Active

    /**
     * Whether this enrolment was begun and its first code has not been typed yet.
     */
    public fun isPending(): Boolean = standing == Standing.Pending

    /**
     * Whether this enrolment's seed was retired because a recovery code was spent in its place.
     */
    public fun isRetired(): Boolean = standing == Standing.Retired

    /**
     * The first code of a pending enrolment, typed to show the app holds the seed.
     *
     * Right: the enrolment is active and remembers the step. Wrong, or not pending: [CodeVerdict.Wrong].
     */
    public fun confirm(code: String, now: Instant): CodeVerdict = judged(code, now, Standing.Pending, Standing.Active)

    /**
     * A code typed to sign in or to confirm a dangerous act.
     *
     * Right and unused: the enrolment stays active and remembers the step. Wrong, used, or the
     * enrolment not active: [CodeVerdict.Wrong].
     */
    public fun check(code: String, now: Instant): CodeVerdict = judged(code, now, Standing.Active, Standing.Active)

    /**
     * This enrolment after a recovery code was spent: an active one is retired, anything else is as it was,
     * since a pending one counted for nothing and a retired one already stopped.
     */
    public fun retired(): TotpEnrollment =
        if (standing == Standing.Active) TotpEnrollment(seed, Standing.Retired, lastAcceptedStep) else this

    /**
     * Tells [provisioned] the key to show a person and the `otpauth://` address that carries it, for
     * an app that scans a QR code.
     *
     * The address names only [issuer]; the account's own label arrives when `identity` publishes one.
     */
    public fun provision(issuer: String, provisioned: Provisioned) {
        val named = URLEncoder.encode(issuer, Charsets.UTF_8).replace("+", "%20")
        val uri = "otpauth://totp/$named?secret=${seed.revealed()}&issuer=$named" +
            "&algorithm=SHA1&digits=$DIGITS&period=$PERIOD_SECONDS"
        provisioned.provisioned(seed, Secret(uri))
    }

    /**
     * Tells [record] everything this enrolment holds.
     */
    public fun writeTo(record: Record) {
        record.kept(seed, standing, lastAcceptedStep)
    }

    private fun judged(code: String, now: Instant, from: Standing, to: Standing): CodeVerdict {
        if (standing != from) {
            return CodeVerdict.Wrong()
        }
        val step = TOTP.matchingStep(BASE32.decode(seed.revealed()), code, now, TOLERANCE, lastAcceptedStep)
        return if (step == null) {
            CodeVerdict.Wrong()
        } else {
            CodeVerdict.Accepted(TotpEnrollment(seed, to, step))
        }
    }

    override fun toString(): String = "TotpEnrollment(standing=$standing)"

    /**
     * Where an enrolment is in its life.
     */
    public enum class Standing {
        /**
         * Begun, and the first code has not been typed. Counts for nothing.
         */
        Pending,

        /**
         * Confirmed: its codes are a factor.
         */
        Active,

        /**
         * Its seed no longer works because a recovery code was spent; only recovery codes do.
         */
        Retired,
    }

    /**
     * Whoever shows a person the key to put in their app.
     */
    public fun interface Provisioned {
        /**
         * The seed in the alphabet an app reads, and the `otpauth://` address that carries it.
         */
        public fun provisioned(key: Secret, uri: Secret)
    }

    /**
     * What an enrolment tells whoever keeps it, and what that keeper tells [restore] to bring it back.
     */
    public fun interface Record {
        /**
         * The enrolment holds [seed], is [standing], and has accepted codes up to the time step
         * [lastAcceptedStep], which is none until the first code was accepted.
         */
        public fun kept(seed: Secret, standing: Standing, lastAcceptedStep: Long?)
    }

    public companion object {
        /**
         * A new, pending enrolment over [entropy], at least twenty random bytes (RFC 4226 asks for 160
         * bits).
         *
         * @throws IllegalArgumentException for fewer bytes than that.
         */
        public fun begin(entropy: ByteArray): TotpEnrollment {
            require(entropy.size >= MIN_SEED_BYTES) {
                "A TOTP seed needs at least $MIN_SEED_BYTES random bytes, but was given ${entropy.size}."
            }
            return TotpEnrollment(Secret(BASE32.encode(entropy)), Standing.Pending, null)
        }

        /**
         * The enrolment a keeper [replay]s into the [Record] it is handed.
         *
         * @throws IllegalStateException for a replay no enrolment could have told: no seed, not told once,
         * a seed that is not base32, a pending one that has accepted a code, or an active or retired one
         * that has accepted none.
         */
        public fun restore(replay: (Record) -> Unit): TotpEnrollment {
            val told = mutableListOf<TotpEnrollment>()
            replay { seed, standing, lastAcceptedStep ->
                check(told.isEmpty()) { refused("it was told twice") }
                check(runCatching { BASE32.decode(seed.revealed()) }.isSuccess) {
                    refused("the seed is not base32")
                }
                check((standing == Standing.Pending) == (lastAcceptedStep == null)) {
                    refused("a pending enrolment has accepted no code and any other has accepted one")
                }
                told += TotpEnrollment(seed, standing, lastAcceptedStep)
            }
            return checkNotNull(told.singleOrNull()) { refused("it was never told") }
        }

        private fun refused(reason: String): String =
            "A stored TOTP enrolment cannot be restored: $reason. TotpEnrollment.writeTo never says that, " +
                "so the stored row was changed by something else. Do not repair it by hand: the person " +
                "disables TOTP and enables it again."

        private const val MIN_SEED_BYTES = 20
        private const val TOLERANCE = 1
        private const val DIGITS = 6
        private const val PERIOD_SECONDS = 30
        private val BASE32 = Base32()
        private val TOTP = Rfc6238Totp()
    }
}
