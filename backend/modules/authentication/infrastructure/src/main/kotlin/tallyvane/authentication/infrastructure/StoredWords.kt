package tallyvane.authentication.infrastructure

import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.Step

/**
 * How the domain's closed vocabularies are spelled in the tables, and back.
 *
 * Each direction is written out, not derived from the name: a new [FactorKind] that is not given a
 * word here stops compiling, instead of reaching the database as a value its `check` refuses. The
 * words are the ones the migration's `check` constraints list; a spec per vocabulary keeps the two
 * in step.
 */
internal class StoredWords {
    fun of(purpose: Purpose): String = when (purpose) {
        Purpose.Registration -> "registration"
        Purpose.Login -> "login"
        Purpose.AdminLogin -> "admin_login"
        Purpose.StepUp -> "step_up"
    }

    fun of(kind: FactorKind): String = when (kind) {
        FactorKind.Google -> "google"
        FactorKind.Totp -> "totp"
        FactorKind.RecoveryCode -> "recovery_code"
    }

    fun of(necessity: Step.Necessity): String = when (necessity) {
        Step.Necessity.Always -> "always"
        Step.Necessity.WhenEnrolled -> "when_enrolled"
    }

    fun purposeFrom(word: String): Purpose = Purpose.entries.readFrom(word, ::of)

    fun kindFrom(word: String): FactorKind = FactorKind.entries.readFrom(word, ::of)

    fun necessityFrom(word: String): Step.Necessity = Step.Necessity.entries.readFrom(word, ::of)

    private fun <T> List<T>.readFrom(word: String, spelled: (T) -> String): T = singleOrNull { spelled(it) == word }
        ?: error(
            "The database holds '$word', which the code does not know. A migration added a value " +
                "without the code that reads it; add the word to StoredWords or fix the migration.",
        )
}
