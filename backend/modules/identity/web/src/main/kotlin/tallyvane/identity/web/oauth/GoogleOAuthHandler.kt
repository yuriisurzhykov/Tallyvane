package tallyvane.identity.web.oauth

import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import kotlinx.serialization.Serializable
import tallyvane.identity.application.IdentityUseCases
import tallyvane.identity.application.googleoauth.UnlinkGoogleAccountUseCase
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.secondfactor.AuthenticationTokenKind
import tallyvane.identity.web.login.AuthenticationProblems
import tallyvane.identity.web.login.SignInResponses
import tallyvane.identity.web.shared.CurrentPrincipal
import tallyvane.platform.http.Refused

/**
 * OAuth Authorization Code + PKCE entry and callback. State and verifier are short-lived, HttpOnly cookies.
 */
internal interface GoogleOAuthHandler {
    suspend fun start(call: io.ktor.server.application.ApplicationCall)
    suspend fun startLink(call: io.ktor.server.application.ApplicationCall)
    suspend fun startReauthentication(call: io.ktor.server.application.ApplicationCall)
    suspend fun startActionProof(call: io.ktor.server.application.ApplicationCall)
    suspend fun unlink(call: io.ktor.server.application.ApplicationCall)
    suspend fun status(call: io.ktor.server.application.ApplicationCall)
    suspend fun callback(call: io.ktor.server.application.ApplicationCall)

    data class Services(
        val responses: SignInResponses,
        val current: CurrentPrincipal,
        val authenticationProblems: AuthenticationProblems,
    )

    class Redirector(
        private val configuration: GoogleOAuthConfiguration,
        private val cases: IdentityUseCases,
        private val services: Services,
        callback: GoogleOAuthCallback? = null,
    ) : GoogleOAuthHandler {
        private val stateCookie = GoogleOAuthStateCookie.Cookie()
        private val callbackResponses = GoogleOAuthCallbackResponses.Redirector()
        private val callbackHandler = callback ?: GoogleOAuthCallback.Handler(
            configuration,
            cases,
            GoogleOAuthCallback.Services(services.responses, services.current, callbackResponses),
        )

        override suspend fun start(call: io.ktor.server.application.ApplicationCall) {
            if (AuthenticationTokenKind.GOOGLE !in cases.readSignInOptions.read()) {
                call.respond(
                    Refused(
                        tallyvane.identity.web.login.AuthenticationFailure.InvalidCredential,
                        services.authenticationProblems,
                    ),
                )
                return
            }
            val challenge = PkceChallenge.Generator().generate()
            setCookie(call, stateCookie.signIn(challenge))
            call.respondRedirect(authorizationLocation(challenge), permanent = false)
        }

        override suspend fun startLink(call: io.ktor.server.application.ApplicationCall) {
            val identity = services.current.resolve(call) ?: return
            val body = call.receive<LinkStartBody>()
            if (body.actionProof.isBlank()) {
                call.respond(
                    Refused(
                        tallyvane.identity.web.login.AuthenticationFailure.InvalidCredential,
                        services.authenticationProblems,
                    ),
                )
                return
            }
            val challenge = PkceChallenge.Generator().generate()
            setCookie(call, stateCookie.link(challenge, identity, body.actionProof))
            call.respond(mapOf("url" to authorizationLocation(challenge)))
        }

        override suspend fun startReauthentication(call: io.ktor.server.application.ApplicationCall) {
            val identity = services.current.resolve(call) ?: return
            val challenge = PkceChallenge.Generator().generate()
            setCookie(call, stateCookie.reauthenticate(challenge, identity.userId, identity.sessionId))
            call.respondRedirect(authorizationLocation(challenge), permanent = false)
        }

        override suspend fun startActionProof(call: io.ktor.server.application.ApplicationCall) {
            val identity = services.current.resolve(call) ?: return
            val action = runCatching { AuthenticationAction.valueOf(call.receive<ActionProofStartBody>().action) }
                .getOrNull()
            val availableAction = action?.takeIf { candidate ->
                cases.readAuthenticationActionSchemes?.read(
                    identity.userId,
                    identity.sessionId,
                    candidate,
                ).orEmpty().any { AuthenticationTokenKind.GOOGLE in it.requiredTokens }
            }
            if (availableAction == null) {
                call.respond(
                    Refused(
                        tallyvane.identity.web.login.AuthenticationFailure.InvalidCredential,
                        services.authenticationProblems,
                    ),
                )
                return
            }
            val challenge = PkceChallenge.Generator().generate()
            setCookie(call, stateCookie.actionProof(challenge, identity, availableAction))
            call.respond(mapOf("url" to authorizationLocation(challenge)))
        }

        override suspend fun unlink(call: io.ktor.server.application.ApplicationCall) {
            val identity = services.current.resolve(call) ?: return
            when (
                cases.unlinkGoogleAccount.unlink(
                    identity.userId,
                    identity.sessionId,
                    call.request.headers["X-Action-Proof"],
                )
            ) {
                UnlinkGoogleAccountUseCase.Result.Unlinked -> call.respond(HttpStatusCode.NoContent)
                UnlinkGoogleAccountUseCase.Result.InvalidCredential -> call.respond(
                    Refused(GoogleAccountProblems.Failure.InvalidCredential, GoogleAccountProblems.Table()),
                )
                UnlinkGoogleAccountUseCase.Result.LastSignInMethod -> call.respond(
                    Refused(GoogleAccountProblems.Failure.LastSignInMethod, GoogleAccountProblems.Table()),
                )
                UnlinkGoogleAccountUseCase.Result.NotLinked -> call.respond(
                    Refused(GoogleAccountProblems.Failure.NotLinked, GoogleAccountProblems.Table()),
                )
            }
        }

        override suspend fun status(call: io.ktor.server.application.ApplicationCall) {
            val identity = services.current.resolve(call) ?: return
            call.respond(mapOf("googleLinked" to cases.readGoogleAccountLink.isLinked(identity.userId)))
        }

        private fun authorizationLocation(challenge: PkceChallenge.Generated): String =
            URLBuilder(AUTHORIZATION_ENDPOINT).apply {
                parameters.append("client_id", configuration.clientId)
                parameters.append("redirect_uri", configuration.redirectUri)
                parameters.append("response_type", "code")
                parameters.append("scope", "openid email profile")
                parameters.append("state", challenge.state)
                parameters.append("code_challenge", challenge.challenge)
                parameters.append("code_challenge_method", "S256")
            }.buildString()

        private fun setCookie(call: io.ktor.server.application.ApplicationCall, value: String) {
            call.response.cookies.append(
                Cookie(
                    COOKIE,
                    value,
                    path = configuration.cookiePath,
                    secure = configuration.secure,
                    httpOnly = true,
                    extensions = mapOf("SameSite" to "Lax"),
                    maxAge = 300,
                ),
            )
        }

        override suspend fun callback(call: io.ktor.server.application.ApplicationCall) = callbackHandler.handle(call)

        private data class LinkStartBody(val actionProof: String)

        @Serializable
        private data class ActionProofStartBody(val action: String)

        private companion object {
            const val AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
            const val COOKIE = "tallyvane_google_oauth"
        }
    }
}
