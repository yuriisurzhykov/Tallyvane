package tallyvane.authentication.web

import kotlinx.serialization.Serializable

/**
 * What the signed-in person has set up as a second factor: [standing] is `off`, `active` or `retired`,
 * and [recoveryCodesRemaining] counts the recovery codes still unspent.
 */
@Serializable
internal class SecondFactorState(val standing: String, val recoveryCodesRemaining: Int)
