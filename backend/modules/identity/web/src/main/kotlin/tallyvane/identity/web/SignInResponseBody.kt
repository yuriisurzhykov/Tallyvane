package tallyvane.identity.web

import kotlinx.serialization.Serializable

/**
 * One shape for every sign-in-shaped outcome: `"issued"` (raw tokens are in the cookies
 * [SessionCookies] just attached, never here) or `"requires_second_factor"`, in which case
 * [pendingId]/[availableMethods] are present and the client's next call is `POST /auth/mfa/verify`.
 */
@Serializable
internal data class SignInResponseBody(
    val status: String,
    val pendingId: String? = null,
    val availableMethods: List<String>? = null,
)
