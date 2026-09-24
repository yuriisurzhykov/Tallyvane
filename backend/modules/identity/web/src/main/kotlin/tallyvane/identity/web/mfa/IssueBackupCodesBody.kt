package tallyvane.identity.web.mfa

import kotlinx.serialization.Serializable

@Serializable
internal data class IssueBackupCodesBody(val currentPassword: String)
