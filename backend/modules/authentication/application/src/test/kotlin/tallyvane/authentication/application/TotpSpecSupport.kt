package tallyvane.authentication.application

import tallyvane.identity.contract.AccountId

/**
 * Helpers the specs of the TOTP use cases share.
 */
internal fun shownAs(shown: SecondFactorShown): String = shown.reportTo(
    object : SecondFactorShown.Report<String> {
        override fun off(): String = "off"

        override fun active(recoveryCodesLeft: Int): String = "active, $recoveryCodesLeft codes"

        override fun retired(recoveryCodesLeft: Int): String = "retired, $recoveryCodesLeft codes"
    },
)

internal fun toldAs(verification: Verification): String = when (verification) {
    is Verification.Verified -> verification.reportTo(
        object : Verification.Verified.Report<String> {
            override fun totp(): String = "verified by code"

            override fun recovery(remaining: Int): String = "verified by recovery code, $remaining left"
        },
    )

    is Verification.Failed -> verification.toString()
}

internal fun keyOf(begun: TotpBegun): String {
    val keys = mutableListOf<String>()
    (begun as TotpBegun.Started).writeTo { key, _ -> keys += key.revealed() }
    return keys.single()
}

internal fun codesOf(regenerated: CodesRegenerated): List<String> {
    val shown = mutableListOf<String>()
    (regenerated as CodesRegenerated.Regenerated).writeTo { codes -> shown += codes.map { it.revealed() } }
    return shown
}

/**
 * Runs [body] with a [Harness] in which `sub-1` has an account, and that account.
 */
internal suspend fun withAccount(body: suspend (Harness, AccountId) -> Unit) {
    val harness = Harness()
    harness.accounts.knows("sub-1")
    body(harness, checkNotNull(harness.accounts.withGoogle("sub-1")))
}
