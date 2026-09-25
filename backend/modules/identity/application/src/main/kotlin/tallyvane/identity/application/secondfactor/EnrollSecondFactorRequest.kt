package tallyvane.identity.application.secondfactor

import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.user.UserId
import tallyvane.identity.domain.session.SessionId

public data class EnrollSecondFactorRequest(
    public val userId: UserId,
    public val kind: SecondFactorKind,
    public val sessionId: SessionId? = null,
    public val actionProof: String? = null,
)
