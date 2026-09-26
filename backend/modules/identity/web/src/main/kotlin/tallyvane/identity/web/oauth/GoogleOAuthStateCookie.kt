package tallyvane.identity.web.oauth

import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.user.UserId
import kotlin.uuid.Uuid

internal interface GoogleOAuthStateCookie {
    fun signIn(challenge: PkceChallenge.Generated): String
    fun link(challenge: PkceChallenge.Generated, userId: UserId, sessionId: SessionId, actionProof: String): String
    fun reauthenticate(challenge: PkceChallenge.Generated, userId: UserId, sessionId: SessionId): String
    fun actionProof(
        challenge: PkceChallenge.Generated,
        userId: UserId,
        sessionId: SessionId,
        action: AuthenticationAction,
    ): String
    fun read(cookie: String?, state: String?): State?

    sealed interface State {
        val codeVerifier: String
        data class SignIn(override val codeVerifier: String) : State
        data class Link(
            override val codeVerifier: String,
            val userId: UserId,
            val sessionId: SessionId,
            val actionProof: String,
        ) : State
        data class Reauthenticate(override val codeVerifier: String, val userId: UserId, val sessionId: SessionId) :
            State
        data class ActionProof(
            override val codeVerifier: String,
            val userId: UserId,
            val sessionId: SessionId,
            val action: AuthenticationAction,
            val state: String,
        ) : State
    }

    class Cookie : GoogleOAuthStateCookie {
        override fun signIn(challenge: PkceChallenge.Generated): String =
            listOf("signin", challenge.state, challenge.verifier).joinToString(".")

        override fun link(
            challenge: PkceChallenge.Generated,
            userId: UserId,
            sessionId: SessionId,
            actionProof: String,
        ): String =
            listOf("link", challenge.state, challenge.verifier, userId.value, sessionId.value, actionProof).joinToString(".")

        override fun reauthenticate(challenge: PkceChallenge.Generated, userId: UserId, sessionId: SessionId): String =
            listOf("reauth", challenge.state, challenge.verifier, userId.value, sessionId.value).joinToString(".")

        override fun actionProof(
            challenge: PkceChallenge.Generated,
            userId: UserId,
            sessionId: SessionId,
            action: AuthenticationAction,
        ): String = listOf(
            "proof", challenge.state, challenge.verifier, userId.value, sessionId.value, action.name,
        ).joinToString(".")

        override fun read(cookie: String?, state: String?): State? {
            val parts = cookie?.split('.')
            return when {
                parts == null || state == null || parts.size < SIGN_IN_PARTS -> null
                !constantTimeEquals(parts[STATE_INDEX], state) -> null
                parts[MODE_INDEX] == "signin" && parts.size == SIGN_IN_PARTS ->
                    State.SignIn(parts[VERIFIER_INDEX])
                parts[MODE_INDEX] == "link" && parts.size == LINK_PARTS -> parseLink(parts)
                parts[MODE_INDEX] == "reauth" && parts.size == LINK_PARTS -> parseReauthentication(parts)
                parts[MODE_INDEX] == "proof" && parts.size == LINK_PARTS -> parseActionProof(parts)
                else -> null
            }
        }

        private fun parseLink(parts: List<String>): State? = runCatching {
            State.Link(
                parts[VERIFIER_INDEX],
                UserId(Uuid.parse(parts[USER_INDEX])),
                SessionId(Uuid.parse(parts[SESSION_INDEX])),
                parts[PROOF_INDEX],
            )
        }.getOrNull()

        private fun parseReauthentication(parts: List<String>): State? = runCatching {
            State.Reauthenticate(
                parts[VERIFIER_INDEX],
                UserId(Uuid.parse(parts[USER_INDEX])),
                SessionId(Uuid.parse(parts[SESSION_INDEX])),
            )
        }.getOrNull()

        private fun parseActionProof(parts: List<String>): State? = runCatching {
            State.ActionProof(
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
            const val LINK_PARTS = 6
        }
    }
}
