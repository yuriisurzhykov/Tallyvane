package tallyvane.identity.application.secondfactor

import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import kotlin.uuid.Uuid

public data class VerifySecondFactorRequest(
    public val pendingId: PendingAuthenticationId,
    public val kind: SecondFactorKind,
    public val code: String,
    public val challengeId: Uuid? = null,
)
