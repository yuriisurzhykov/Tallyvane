package tallyvane.identity.web.oauth

import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tallyvane.identity.application.SignInOutcome
import tallyvane.identity.application.googleoauth.LinkGoogleAccountUseCase
import tallyvane.identity.application.googleoauth.ReadGoogleAccountLinkUseCase
import tallyvane.identity.application.googleoauth.SignInWithGoogleOAuthRequest
import tallyvane.identity.application.googleoauth.SignInWithGoogleOAuthUseCase
import tallyvane.identity.application.googleoauth.UnlinkGoogleAccountUseCase
import tallyvane.identity.application.password.ReauthenticateUseCase
import tallyvane.identity.application.secondfactor.AuthorizeAuthenticationActionUseCase
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.secondfactor.AuthenticationTokenKind
import tallyvane.identity.domain.session.DeviceLabel
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

    class Redirector(
        private val clientId: String,
        private val redirectUri: String,
        private val signIn: SignInWithGoogleOAuthUseCase,
        private val responses: SignInResponses,
        private val secure: Boolean,
        private val linker: LinkGoogleAccountUseCase,
        private val stateCookie: GoogleOAuthStateCookie,
        private val callbackResponses: GoogleOAuthCallbackResponses,
        private val current: CurrentPrincipal,
        private val authenticationProblems: AuthenticationProblems,
        private val unlinker: UnlinkGoogleAccountUseCase,
        private val accountProblems: GoogleAccountProblems,
        private val linkStatus: ReadGoogleAccountLinkUseCase,
        private val reauthenticate: ReauthenticateUseCase,
        private val authorizeAction: AuthorizeAuthenticationActionUseCase?,
    ) : GoogleOAuthHandler {
        override suspend fun start(call: io.ktor.server.application.ApplicationCall) {
            val challenge = PkceChallenge.Generator().generate()
            setCookie(call, stateCookie.signIn(challenge))
            call.respondRedirect(authorizationLocation(challenge), permanent = false)
        }

        override suspend fun startLink(call: io.ktor.server.application.ApplicationCall) {
            val identity = current.resolve(call) ?: return
            val body = call.receive<LinkStartBody>()
            if (body.actionProof.isBlank()) {
                call.respond(
                    Refused(
                        tallyvane.identity.web.login.AuthenticationFailure.InvalidCredential,
                        authenticationProblems,
                    ),
                )
                return
            }
            val challenge = PkceChallenge.Generator().generate()
            setCookie(call, stateCookie.link(challenge, identity.userId, identity.sessionId, body.actionProof))
            call.respond(mapOf("url" to authorizationLocation(challenge)))
        }

        override suspend fun startReauthentication(call: io.ktor.server.application.ApplicationCall) {
            val identity = current.resolve(call) ?: return
            val challenge = PkceChallenge.Generator().generate()
            setCookie(call, stateCookie.reauthenticate(challenge, identity.userId, identity.sessionId))
            call.respondRedirect(authorizationLocation(challenge), permanent = false)
        }

        override suspend fun startActionProof(call: io.ktor.server.application.ApplicationCall) {
            val identity = current.resolve(call) ?: return
            val action = runCatching { AuthenticationAction.valueOf(call.receive<ActionProofStartBody>().action) }
                .getOrNull()
            val available = action?.let {
                authorizeAction?.strongestSchemes(identity.userId, identity.sessionId, it)
            }.orEmpty().any { AuthenticationTokenKind.GOOGLE in it.requiredTokens }
            if (!available) {
                call.respond(
                    Refused(tallyvane.identity.web.login.AuthenticationFailure.InvalidCredential, authenticationProblems),
                )
                return
            }
            val challenge = PkceChallenge.Generator().generate()
            setCookie(call, stateCookie.actionProof(challenge, identity.userId, identity.sessionId, action!!))
            call.respond(mapOf("url" to authorizationLocation(challenge)))
        }

        override suspend fun unlink(call: io.ktor.server.application.ApplicationCall) {
            val identity = current.resolve(call) ?: return
            when (unlinker.unlink(identity.userId, identity.sessionId, call.request.headers["X-Action-Proof"])) {
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
            val reauthenticationAttempt = cookie?.startsWith("reauth.") == true
            val actionProofAttempt = cookie?.startsWith("proof.") == true
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
                if (oauth is GoogleOAuthStateCookie.State.ActionProof || actionProofAttempt) {
                    completeActionProofPopup(call, oauth as? GoogleOAuthStateCookie.State.ActionProof, null)
                } else if (oauth is GoogleOAuthStateCookie.State.Link || linkAttempt) {
                    val result = if (providerError == "access_denied") "cancelled" else "failed"
                    callbackResponses.linkFailure(call, result)
                } else if (oauth is GoogleOAuthStateCookie.State.Reauthenticate || reauthenticationAttempt) {
                    callbackResponses.reauthentication(
                        call,
                        if (providerError == "access_denied") "cancelled" else "failed",
                    )
                } else {
                    callbackResponses.providerError(call, providerError)
                }
                return
            }
            val code = query["code"]
            if (code == null || oauth == null) {
                if (actionProofAttempt) {
                    completeActionProofPopup(call, oauth as? GoogleOAuthStateCookie.State.ActionProof, null)
                } else if (linkAttempt) {
                    callbackResponses.linkFailure(call, "failed")
                } else if (reauthenticationAttempt) {
                    callbackResponses.reauthentication(call, "failed")
                } else {
                    callbackResponses.oauthFailure(call)
                }
                return
            }
            when (oauth) {
                is GoogleOAuthStateCookie.State.ActionProof -> completeActionProofPopup(call, oauth, code)
                is GoogleOAuthStateCookie.State.Link -> completeLink(call, oauth, code)
                is GoogleOAuthStateCookie.State.Reauthenticate -> completeReauthentication(call, oauth, code)
                is GoogleOAuthStateCookie.State.SignIn -> {
                    val outcome = signIn.signIn(
                        SignInWithGoogleOAuthRequest(code, oauth.codeVerifier, redirectUri, DeviceLabel("Browser")),
                    )
                    completeSignIn(call, outcome)
                }
            }
        }

        private suspend fun completeActionProofPopup(
            call: io.ktor.server.application.ApplicationCall,
            oauth: GoogleOAuthStateCookie.State.ActionProof?,
            code: String?,
        ) {
            val identity = current.peek(call)
            val accepted = oauth != null && code != null && identity?.userId == oauth.userId &&
                identity.sessionId == oauth.sessionId
            val payload = GoogleActionProofPopupBody(
                type = "tallyvane-google-action-proof",
                state = oauth?.state ?: call.request.queryParameters["state"].orEmpty(),
                value = if (accepted) code.orEmpty() else "",
                codeVerifier = if (accepted) oauth.codeVerifier else "",
                redirectUri = if (accepted) redirectUri else "",
                error = !accepted,
            )
            val data = Json.encodeToString(payload).replace("<", "\\u003c")
            call.response.headers.append("Cache-Control", "no-store")
            call.respondText(
                "<!doctype html><html><body><script>window.opener?.postMessage($data, window.location.origin);window.close();</script></body></html>",
                ContentType.Text.Html,
            )
        }

        private suspend fun completeReauthentication(
            call: io.ktor.server.application.ApplicationCall,
            oauth: GoogleOAuthStateCookie.State.Reauthenticate,
            code: String,
        ) {
            val identity = current.resolve(call) ?: return
            if (identity.userId != oauth.userId || identity.sessionId != oauth.sessionId) {
                callbackResponses.reauthentication(call, "failed")
                return
            }
            val result = reauthenticate.google(
                oauth.userId,
                oauth.sessionId,
                code,
                oauth.codeVerifier,
                redirectUri,
            )
            callbackResponses.reauthentication(
                call,
                if (result == ReauthenticateUseCase.Outcome.REAUTHENTICATED) "success" else "failed",
            )
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
                LinkGoogleAccountUseCase.Request(
                    oauth.userId, oauth.sessionId, oauth.actionProof, code, oauth.codeVerifier, redirectUri,
                ),
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
                    call.respondRedirect("/auth/callback", permanent = false)
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

        private data class LinkStartBody(val actionProof: String)

        @Serializable
        private data class ActionProofStartBody(val action: String)

        @Serializable
        private data class GoogleActionProofPopupBody(
            val type: String,
            val state: String,
            val value: String,
            val codeVerifier: String,
            val redirectUri: String,
            val error: Boolean,
        )

        private companion object {
            const val AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
            const val COOKIE = "tallyvane_google_oauth"
            const val COOKIE_PATH = "/api/v1/auth/google"
        }
    }
}
