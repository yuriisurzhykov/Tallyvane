package tallyvane.authentication.domain

import kotlin.time.Instant

/**
 * What a method tells the rest of the system: a factor of this kind was verified at this instant,
 * and, for a factor that identifies the account, whose it was.
 *
 * Nothing here says *how*. The how belongs to the method that produced one, which is what makes a new
 * method a new implementation rather than a change to sign-in.
 *
 * The *whose* is here because it cannot be anywhere else (slice 3, fork 4). Google is how an attempt
 * learns whose sign-in it is, and only the factor that told it knows the answer; a later factor, such
 * as a TOTP code, is checked against the account the first one named. So an identifying factor
 * carries the [subject] its provider vouched for, and a confirming one carries none. The two are made
 * by [identifying] and [confirming], which refuse to mix them up.
 *
 * Its values are `internal`: [Attempt] reads them to answer its own questions, and nothing outside
 * this module can.
 */
public class VerifiedFactor private constructor(
    internal val kind: FactorKind,
    internal val at: Instant,
    internal val subject: String?,
) {
    /**
     * This factor as verified no earlier than [floor]: the same factor, moved forward in time if the
     * clock that stamped it was behind (slice 3, fork 5).
     */
    internal fun notBefore(floor: Instant): VerifiedFactor = VerifiedFactor(kind, maxOf(at, floor), subject)

    /**
     * Whether this factor names a different person than [other] does. Two factors that name nobody,
     * or the same somebody, do not.
     */
    internal fun disagreesWith(other: VerifiedFactor): Boolean =
        subject != null && other.subject != null && subject != other.subject

    internal fun writeTo(record: Attempt.Record) {
        subject?.let { record.identified(kind, it, at) } ?: record.verified(kind, at)
    }

    override fun equals(other: Any?): Boolean =
        other is VerifiedFactor && other.kind == kind && other.at == at && other.subject == subject

    override fun hashCode(): Int = (kind.hashCode() * 31 + at.hashCode()) * 31 + subject.hashCode()

    // The subject is an account's name at its provider, not a secret, and still not something a log of
    // a failed sign-in needs: the kind says enough.
    override fun toString(): String = "VerifiedFactor(kind=$kind, at=$at)"

    public companion object {
        /**
         * A factor that tells whose account this is: [kind] vouched at [at] for the [subject] its
         * provider knows the person by, such as Google's `sub`.
         *
         * @throws IllegalArgumentException for a kind that does not identify an account, or a blank
         * subject.
         */
        public fun identifying(kind: FactorKind, subject: String, at: Instant): VerifiedFactor {
            require(kind.identifiesTheAccount()) {
                "$kind confirms an account somebody else already identified; it cannot name one."
            }
            require(subject.isNotBlank()) { "A provider that names nobody has identified nobody." }
            return VerifiedFactor(kind, at, subject)
        }

        /**
         * A factor that confirms the account an earlier one identified: [kind] was verified at [at].
         *
         * @throws IllegalArgumentException for a kind that identifies an account, which must say whose.
         */
        public fun confirming(kind: FactorKind, at: Instant): VerifiedFactor {
            require(!kind.identifiesTheAccount()) {
                "$kind identifies an account, so it must say whose: use identifying(kind, subject, at)."
            }
            return VerifiedFactor(kind, at, null)
        }
    }
}
