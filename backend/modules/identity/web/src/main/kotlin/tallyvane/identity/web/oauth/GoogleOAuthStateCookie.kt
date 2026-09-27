package tallyvane.identity.web.oauth

import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.user.UserId
import tallyvane.identity.web.shared.ResolvedIdentity

internal interface GoogleOAuthStateCookie {
    fun signIn(challenge: PkceChallenge.Generated): String
    fun link(challenge: PkceChallenge.Generated, identity: ResolvedIdentity, actionProof: String): String
    fun reauthenticate(challenge: PkceChallenge.Generated, userId: UserId, sessionId: SessionId): String
    fun actionProof(
        challenge: PkceChallenge.Generated,
        identity: ResolvedIdentity,
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
            identity: ResolvedIdentity,
            actionProof: String,
        ): String = listOf(
            "link",
            challenge.state,
            challenge.verifier,
            identity.userId.value,
            identity.sessionId.value,
            actionProof,
        ).joinToString(".")

        override fun reauthenticate(challenge: PkceChallenge.Generated, userId: UserId, sessionId: SessionId): String =
            listOf("reauth", challenge.state, challenge.verifier, userId.value, sessionId.value).joinToString(".")

        override fun actionProof(
            challenge: PkceChallenge.Generated,
            identity: ResolvedIdentity,
            action: AuthenticationAction,
        ): String = listOf(
            "proof",
            challenge.state,
            challenge.verifier,
            identity.userId.value,
            identity.sessionId.value,
            action.name,
        ).joinToString(".")

        override fun read(cookie: String?, state: String?): State? = GoogleOAuthStateParser().read(cookie, state)
    }
}
