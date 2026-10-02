package tallyvane.sessions.infrastructure

import tallyvane.sessions.domain.Factor

/**
 * The words the migration's `check` constraint accepts for each [Factor], and back.
 *
 * Spelled out rather than taken from the enum's names, so renaming a constant cannot change what is
 * stored.
 */
internal class StoredFactors {
    fun of(factor: Factor): String = when (factor) {
        Factor.Google -> "google"
        Factor.Totp -> "totp"
        Factor.RecoveryCode -> "recovery_code"
    }

    fun from(word: String): Factor = Factor.entries.firstOrNull { of(it) == word }
        ?: error("A session was kept as proved by '$word', which the sessions schema does not accept.")

    override fun toString(): String = "StoredFactors"
}
