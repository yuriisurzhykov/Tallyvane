package tallyvane.authentication.domain

/**
 * A way a person can prove who they are (ADR-078).
 *
 * The rest of the module never asks *how* a factor was checked: a method verifies one kind and
 * reports a [VerifiedFactor], and a [SignInPolicy] decides whether the kinds collected so far are
 * enough. Adding a kind here is adding a word to that vocabulary; nothing that evaluates a policy
 * changes.
 *
 * @param needsEnrollment Whether the account must have set this factor up before it can be
 * verified. Google does not: it is how an account is found in the first place.
 */
public enum class FactorKind(private val needsEnrollment: Boolean) {
    Google(needsEnrollment = false),
    Totp(needsEnrollment = true),
    RecoveryCode(needsEnrollment = true),
    ;

    /**
     * Whether an account with this [enrollment] can verify this kind at all.
     *
     * This is what lets a policy tell "not verified yet" apart from "cannot be verified until the
     * person sets it up".
     */
    public fun isAvailableTo(enrollment: Enrollment): Boolean = !needsEnrollment || enrollment.includes(this)

    /**
     * Whether verifying this kind tells whose account it is. A kind nobody has to set up is one the
     * provider vouches for on its own, so it names the person; a kind that needs setting up can only
     * confirm somebody already named.
     */
    internal fun identifiesTheAccount(): Boolean = !needsEnrollment
}
