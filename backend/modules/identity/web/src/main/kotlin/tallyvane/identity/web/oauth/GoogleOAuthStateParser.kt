package tallyvane.identity.web.oauth

import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.user.UserId
import kotlin.uuid.Uuid

internal class GoogleOAuthStateParser {
    internal fun read(cookie: String?, state: String?): GoogleOAuthStateCookie.State? =
        validatedParts(cookie, state)?.let(::parseState)

    private fun validatedParts(cookie: String?, state: String?): List<String>? {
        val parts = cookie?.split('.')
        return parts?.takeIf { candidate ->
            state != null &&
                candidate.size >= SIGN_IN_PARTS &&
                constantTimeEquals(candidate[STATE_INDEX], state)
        }
    }

    private fun parseState(parts: List<String>): GoogleOAuthStateCookie.State? = when (parts[MODE_INDEX]) {
        "signin" -> parts.takeIf { it.size == SIGN_IN_PARTS }
            ?.let { GoogleOAuthStateCookie.State.SignIn(it[VERIFIER_INDEX]) }
        "reauth" -> parts.takeIf { it.size == REAUTH_PARTS }?.let(::parseReauthentication)
        "link" -> parts.takeIf { it.size == LINK_PARTS }?.let(::parseLink)
        "proof" -> parts.takeIf { it.size == LINK_PARTS }?.let(::parseActionProof)
        else -> null
    }

    private fun parseLink(parts: List<String>): GoogleOAuthStateCookie.State? = runCatching {
        GoogleOAuthStateCookie.State.Link(
            parts[VERIFIER_INDEX],
            UserId(Uuid.parse(parts[USER_INDEX])),
            SessionId(Uuid.parse(parts[SESSION_INDEX])),
            parts[PROOF_INDEX],
        )
    }.getOrNull()

    private fun parseReauthentication(parts: List<String>): GoogleOAuthStateCookie.State? = runCatching {
        GoogleOAuthStateCookie.State.Reauthenticate(
            parts[VERIFIER_INDEX],
            UserId(Uuid.parse(parts[USER_INDEX])),
            SessionId(Uuid.parse(parts[SESSION_INDEX])),
        )
    }.getOrNull()

    private fun parseActionProof(parts: List<String>): GoogleOAuthStateCookie.State? = runCatching {
        GoogleOAuthStateCookie.State.ActionProof(
            parts[VERIFIER_INDEX],
            UserId(Uuid.parse(parts[USER_INDEX])),
            SessionId(Uuid.parse(parts[SESSION_INDEX])),
            AuthenticationAction.valueOf(parts[PROOF_INDEX]),
            parts[STATE_INDEX],
        )
    }.getOrNull()

    private fun constantTimeEquals(left: String, right: String): Boolean =
        java.security.MessageDigest.isEqual(left.toByteArray(Charsets.UTF_8), right.toByteArray(Charsets.UTF_8))

    private companion object {
        const val MODE_INDEX = 0
        const val STATE_INDEX = 1
        const val VERIFIER_INDEX = 2
        const val USER_INDEX = 3
        const val SESSION_INDEX = 4
        const val PROOF_INDEX = 5
        const val SIGN_IN_PARTS = 3
        const val REAUTH_PARTS = 5
        const val LINK_PARTS = 6
    }
}
