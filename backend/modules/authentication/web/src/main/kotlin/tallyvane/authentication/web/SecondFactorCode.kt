package tallyvane.authentication.web

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tallyvane.authentication.application.Submission

/**
 * What a person types to answer a second step: [kind] says which, [code] is the text.
 */
@Serializable
internal class SecondFactorCode(val kind: Kind, val code: String) {
    /**
     * What the person typed is taken for the use case.
     */
    fun submission(): Submission = when (kind) {
        Kind.Totp -> Submission.TotpCode(code)
        Kind.RecoveryCode -> Submission.RecoveryCode(code)
    }

    /**
     * The kinds of answer. Any other word is a body the API cannot read (`400`).
     */
    @Serializable
    enum class Kind {
        @SerialName("totp")
        Totp,

        @SerialName("recovery_code")
        RecoveryCode,
    }
}
