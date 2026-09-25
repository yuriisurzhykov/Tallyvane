package tallyvane.identity.web.oauth

import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import tallyvane.identity.application.SignInOutcome
import tallyvane.identity.application.googleoauth.LinkGoogleAccountUseCase
import tallyvane.identity.application.googleoauth.ReadGoogleAccountLinkUseCase
import tallyvane.identity.application.googleoauth.SignInWithGoogleOAuthRequest
import tallyvane.identity.application.googleoauth.SignInWithGoogleOAuthUseCase
import tallyvane.identity.application.googleoauth.UnlinkGoogleAccountUseCase
import tallyvane.identity.application.password.VerifyPasswordUseCase
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.web.login.AuthenticationProblems
import tallyvane.identity.web.login.SignInResponses
import tallyvane.identity.web.shared.CurrentPrincipal
import tallyvane.platform.http.Refused
import tallyvane.platform.kernel.Secret

/**
 * OAuth Authorization Code + PKCE entry and callback. State and verifier are short-lived, HttpOnly cookies.
 */
internal interface GoogleOAuthHandler {
    suspend fun start(call: io.ktor.server.application.ApplicationCall)
    suspend fun startLink(call: io.ktor.server.application.ApplicationCall)
    suspend fun unlink(call: io.ktor.server.application.ApplicationCall)
    suspend fun status(call: io.ktor.server.application.ApplicationCall)
    suspend fun callback(call: io.ktor.server.application.ApplicationCall)

    class Redirector(
        private val clientId: String,
        private val redirectUri: String,
        private val signIn: SignInWithGoogleOAuthUseCase,
        private val responses: SignInResponses,
        private val secure: Boolean,
        private val linker: LinkGoogleAccountUseCase,
        private val verifyPassword: VerifyPasswordUseCase,
        private val stateCookie: GoogleOAuthStateCookie,
        private val callbackResponses: GoogleOAuthCallbackResponses,
        private val current: CurrentPrincipal,
        private val authenticationProblems: AuthenticationProblems,
        private val unlinker: UnlinkGoogleAccountUseCase,
        private val accountProblems: GoogleAccountProblems,
        private val linkStatus: ReadGoogleAccountLinkUseCase,
    ) : GoogleOAuthHandler {
        override suspend fun start(call: io.ktor.server.application.ApplicationCall) {
            val challenge = PkceChallenge.Generator().generate()
            setCookie(call, stateCookie.signIn(challenge))
            call.respondRedirect(authorizationLocation(challenge), permanent = false)
        }

        override suspend fun startLink(call: io.ktor.server.application.ApplicationCall) {
            val identity = current.resolve(call) ?: return
            val body = call.receive<LinkStartBody>()
            if (!verifyPassword.verify(identity.userId, Secret(body.password))) {
                call.respond(
                    Refused(
                        tallyvane.identity.web.login.AuthenticationFailure.InvalidCredential,
                        authenticationProblems,
                    ),
                )
                return
            }
            val challenge = PkceChallenge.Generator().generate()
            setCookie(call, stateCookie.link(challenge, identity.userId, identity.sessionId))
            call.respond(mapOf("url" to authorizationLocation(challenge)))
        }

        override suspend fun unlink(call: io.ktor.server.application.ApplicationCall) {
            val identity = current.resolve(call) ?: return
            val body = call.receive<LinkStartBody>()
            when (unlinker.unlink(identity.userId, Secret(body.password))) {
                UnlinkGoogleAccountUseCase.Result.Unlinked -> call.respond(HttpStatusCode.NoContent)
                UnlinkGoogleAccountUseCase.Result.InvalidCredential ->
                    call.respond(Refused(GoogleAccountProblems.Failure.InvalidCredential, accountProblems))
                UnlinkGoogleAccountUseCase.Result.LastSignInMethod ->
                    call.respond(Refused(GoogleAccountProblems.Failure.LastSignInMethod, accountProblems))
                UnlinkGoogleAccountUseCase.Result.NotLinked -> call.respond(
                    Refused(GoogleAccountProblems.Failure.NotLinked, accountProblems),
                )
            }
        }

        override suspend fun status(call: io.ktor.server.application.ApplicationCall) {
            val identity = current.resolve(call) ?: return
            call.respond(mapOf("googleLinked" to linkStatus.isLinked(identity.userId)))
        }

        private fun authorizationLocation(challenge: PkceChallenge.Generated): String =
            URLBuilder(AUTHORIZATION_ENDPOINT).apply {
                parameters.append("client_id", clientId)
                parameters.append("redirect_uri", redirectUri)
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
                    path = COOKIE_PATH,
                    secure = secure,
                    httpOnly = true,
                    extensions = mapOf("SameSite" to "Lax"),
                    maxAge = 300,
                ),
            )
        }

        override suspend fun callback(call: io.ktor.server.application.ApplicationCall) {
            val query = call.request.queryParameters
            val providerError = query["error"]
            val cookie = call.request.cookies[COOKIE]
            val oauth = stateCookie.read(cookie, query["state"])
            val linkAttempt = cookie?.startsWith("link.") == true
            call.response.cookies.append(
                Cookie(
                    COOKIE,
                    "",
                    path = COOKIE_PATH,
                    secure = secure,
                    httpOnly = true,
                    extensions = mapOf("SameSite" to "Lax"),
                    maxAge = 0,
                ),
            )
            if (providerError != null) {
                if (oauth is GoogleOAuthStateCookie.State.Link || linkAttempt) {
                    val result = if (providerError == "access_denied") "cancelled" else "failed"
                    callbackResponses.linkFailure(call, result)
                } else {
                    callbackResponses.providerError(call, providerError)
                }
                return
            }
            val code = query["code"]
            if (code == null || oauth == null) {
                if (linkAttempt) {
                    callbackResponses.linkFailure(call, "failed")
                } else {
                    callbackResponses.oauthFailure(call)
                }
                return
            }
            when (oauth) {
                is GoogleOAuthStateCookie.State.Link -> completeLink(call, oauth, code)
                is GoogleOAuthStateCookie.State.SignIn -> {
                    val outcome = signIn.signIn(
                        SignInWithGoogleOAuthRequest(code, oauth.codeVerifier, redirectUri, DeviceLabel("Browser")),
                    )
                    completeSignIn(call, outcome)
                }
            }
        }

        private suspend fun completeLink(
            call: io.ktor.server.application.ApplicationCall,
            oauth: GoogleOAuthStateCookie.State.Link,
            code: String,
        ) {
            val identity = current.resolve(call) ?: return
            if (identity.userId != oauth.userId || identity.sessionId != oauth.sessionId) {
                callbackResponses.linkFailure(call, "failed")
                return
            }
            val result = linker.link(
                LinkGoogleAccountUseCase.Request(oauth.userId, code, oauth.codeVerifier, redirectUri),
            )
            val status = when (result) {
                LinkGoogleAccountUseCase.Result.Linked -> "linked"
                LinkGoogleAccountUseCase.Result.AlreadyLinked -> "already_linked"
                LinkGoogleAccountUseCase.Result.SubjectAlreadyLinked -> "already_used"
                LinkGoogleAccountUseCase.Result.InvalidCredential -> "failed"
            }
            callbackResponses.linkFailure(call, status)
        }

        private suspend fun completeSignIn(call: io.ktor.server.application.ApplicationCall, outcome: SignInOutcome) {
            when (outcome) {
                is SignInOutcome.Issued -> {
                    responses.attachIssued(call, outcome)
                    call.respondRedirect("/today", permanent = false)
                }
                is SignInOutcome.NotIssued -> redirectForSignInReason(call, outcome)
            }
        }

        private suspend fun redirectForSignInReason(
            call: io.ktor.server.application.ApplicationCall,
            outcome: SignInOutcome.NotIssued,
        ) {
            when (val reason = outcome.reason) {
                is tallyvane.identity.domain.outcome.AuthenticationOutcome.RequiresSecondFactor ->
                    call.respondRedirect(
                        "/mfa?pending_id=${reason.pendingId.value}&methods=${reason.availableMethods.joinToString(",") {
                            it.name
                        }}",
                        permanent = false,
                    )
                is tallyvane.identity.domain.outcome.AuthenticationOutcome.RequiresEnrollment ->
                    redirectEnrollment(call, reason)
                else -> callbackResponses.oauthFailure(call)
            }
        }

        private suspend fun redirectEnrollment(
            call: io.ktor.server.application.ApplicationCall,
            reason: tallyvane.identity.domain.outcome.AuthenticationOutcome.RequiresEnrollment,
        ) {
            val methods = reason.requiredMethods.joinToString(",") { it.name }
            call.respondRedirect("/mfa/enroll?pending_id=${reason.pendingId.value}&methods=$methods", permanent = false)
        }

        private data class LinkStartBody(val password: String)

        private companion object {
            const val AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
            const val COOKIE = "tallyvane_google_oauth"
            const val COOKIE_PATH = "/api/v1/auth/google"
        }
    }
}
