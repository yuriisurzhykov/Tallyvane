package tallyvane.authentication.web

import kotlinx.serialization.Serializable

/**
 * The answer to a recovery code that was taken: how many are left.
 */
@Serializable
internal class SecondFactorCodeAccepted(val recoveryCodesRemaining: Int)
