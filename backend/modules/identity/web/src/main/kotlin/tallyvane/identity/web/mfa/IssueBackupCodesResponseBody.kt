package tallyvane.identity.web.mfa

import kotlinx.serialization.Serializable

@Serializable
internal data class IssueBackupCodesResponseBody(val codes: List<String>)
