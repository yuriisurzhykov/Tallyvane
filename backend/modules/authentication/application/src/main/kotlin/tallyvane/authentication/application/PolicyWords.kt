package tallyvane.authentication.application

import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.Step

/**
 * The words the API uses for purposes, kinds of factor and the necessity of a step, and back.
 *
 * Spelled out rather than taken from the enums' names, so renaming a constant cannot change the contract. They
 * are the words of the migrations as well (`login`, `admin_login`, `when_enrolled`), which a person reading the
 * database sees too.
 */
public class PolicyWords {
    /**
     * The purpose [word] names, or null for a word that names none.
     */
    public fun purposeNamed(word: String): Purpose? = Purpose.entries.firstOrNull { wordOf(it) == word }

    public fun wordOf(purpose: Purpose): String = when (purpose) {
        Purpose.Registration -> "registration"
        Purpose.Login -> "login"
        Purpose.AdminLogin -> "admin_login"
        Purpose.StepUp -> "step_up"
    }

    /**
     * The kind of factor [word] names, or null for a word that names none.
     */
    public fun kindNamed(word: String): FactorKind? = FactorKind.entries.firstOrNull { wordOf(it) == word }

    public fun wordOf(kind: FactorKind): String = when (kind) {
        FactorKind.Google -> "google"
        FactorKind.Totp -> "totp"
        FactorKind.RecoveryCode -> "recovery_code"
    }

    /**
     * The necessity [word] names, or null for a word that names none.
     */
    public fun necessityNamed(word: String): Step.Necessity? = Step.Necessity.entries.firstOrNull { wordOf(it) == word }

    public fun wordOf(necessity: Step.Necessity): String = when (necessity) {
        Step.Necessity.Always -> "always"
        Step.Necessity.WhenEnrolled -> "when_enrolled"
    }

    /**
     * The fault of a purpose [word] that names none.
     */
    public fun unknownPurpose(word: String): Fault = Fault("purpose", "unknown", "There is no purpose called '$word'.")

    /**
     * The fault of a comment that is empty, too long or holds a control character.
     */
    public fun invalidComment(): Fault = Fault(
        "comment",
        "comment-invalid",
        "Say why in one to ${Change.MAX_COMMENT_LENGTH} characters, without control characters.",
    )

    override fun toString(): String = "PolicyWords"
}
