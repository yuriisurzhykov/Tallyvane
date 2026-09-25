package tallyvane.identity.application.secondfactor

import kotlin.uuid.Uuid

/**
 * Proof fields common to factors; only email challenges use the challenge and operation binding.
 */
public data class SecondFactorProof(
    public val code: String,
    public val challengeId: Uuid? = null,
    public val binding: String? = null,
)
