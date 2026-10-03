package tallyvane.authentication.web

import kotlinx.serialization.Serializable

/**
 * Ten recovery codes, told once, in the form a person writes down.
 */
@Serializable
internal class RecoveryCodesIssued(val recoveryCodes: List<String>)
