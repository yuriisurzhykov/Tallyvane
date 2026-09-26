package tallyvane.identity.application.secondfactor

import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.user.UserId

public data class ConfirmSecondFactorEnrollmentRequest(
    public val userId: UserId,
    public val kind: SecondFactorKind,
    public val code: String,
)
