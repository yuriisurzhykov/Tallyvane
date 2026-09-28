package tallyvane.identity.web.oauth

import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tallyvane.identity.application.IdentityUseCases
import tallyvane.identity.application.SignInOutcome
import tallyvane.identity.application.googleoauth.LinkGoogleAccountUseCase
import tallyvane.identity.application.googleoauth.SignInWithGoogleOAuthRequest
import tallyvane.identity.application.password.ReauthenticateUseCase
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.web.login.SignInResponses
import tallyvane.identity.web.shared.CurrentPrincipal

internal interface GoogleOAuthCallback {
    suspend fun handle(call: ApplicationCall)

    data class Services(
        val responses: SignInResponses,
        val current: CurrentPrincipal,
        val responsesWriter: GoogleOAuthCallbackResponses,
    )

    class Handler(configuration: GoogleOAuthConfiguration, cases: IdentityUseCases, services: Services) :
        GoogleOAuthCallback {
        private val redirectUri = configuration.redirectUri
        private val secure = configuration.secure
        private val cookiePath = configuration.cookiePath
        private val signIn = requireNotNull(cases.signInWithGoogleOAuth)
        private val responses = services.responses
        private val linker = requireNotNull(cases.linkGoogleAccount)
        private val stateCookie = GoogleOAuthStateCookie.Cookie()
        private val callbackResponses = services.responsesWriter
        private val current = services.current
        private val reauthenticate = cases.reauthenticate
        private val cookieName = COOKIE

        override suspend fun handle(call: ApplicationCall) {
            val query = call.request.queryParameters
            val cookie = call.request.cookies[cookieName]
            val oauth = stateCookie.read(cookie, query["state"])
            val context = CallbackContext(oauth, Attempt(cookie))
            clearStateCookie(call)

            query["error"]?.let { providerError ->
                handleProviderError(call, providerError, context)
                return
            }

            val code = query["code"]
            if (code == null || oauth == null) {
                handleMissingCodeOrState(call, context)
                return
            }
            completeCallback(call, oauth, code)
        }

        private suspend fun handleProviderError(
            call: ApplicationCall,
            providerError: String,
            context: CallbackContext,
        ) {
            val result = if (providerError == "access_denied") "cancelled" else "failed"
            when {
                context.oauth is GoogleOAuthStateCookie.State.ActionProof || context.attempt.actionProof ->
                    completeActionProofPopup(call, context.oauth as? GoogleOAuthStateCookie.State.ActionProof, null)
                context.oauth is GoogleOAuthStateCookie.State.Link || context.attempt.link ->
                    callbackResponses.linkFailure(call, result)
                context.oauth is GoogleOAuthStateCookie.State.Reauthenticate ->
                    callbackResponses.reauthentication(call, result)
                context.attempt.reauthentication -> callbackResponses.reauthentication(call, result)
                else -> callbackResponses.providerError(call, providerError)
            }
        }

        private suspend fun handleMissingCodeOrState(call: ApplicationCall, context: CallbackContext) {
            when {
                context.attempt.actionProof ->
                    completeActionProofPopup(call, context.oauth as? GoogleOAuthStateCookie.State.ActionProof, null)
                context.attempt.link -> callbackResponses.linkFailure(call, "failed")
                context.attempt.reauthentication -> callbackResponses.reauthentication(call, "failed")
                else -> callbackResponses.oauthFailure(call)
            }
        }

        private suspend fun completeCallback(call: ApplicationCall, oauth: GoogleOAuthStateCookie.State, code: String) {
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
            call: ApplicationCall,
            oauth: GoogleOAuthStateCookie.State.ActionProof?,
            code: String?,
        ) {
            val identity = current.peek(call)
            val acceptedState = oauth?.takeIf {
                code != null && identity?.userId == it.userId && identity.sessionId == it.sessionId
            }
            val acceptedCode = code.takeIf { acceptedState != null }
            val accepted = acceptedCode != null
            val payload = GoogleActionProofPopupBody(
                type = "tallyvane-google-action-proof",
                state = oauth?.state ?: call.request.queryParameters["state"].orEmpty(),
                value = acceptedCode.orEmpty(),
                codeVerifier = acceptedState?.codeVerifier.orEmpty(),
                redirectUri = if (accepted) redirectUri else "",
                error = !accepted,
            )
            val data = Json.encodeToString(payload).replace("<", "\\u003c")
            val html = """
            <!doctype html><html><body><script>
            window.opener?.postMessage($data, window.location.origin);
            window.close();
            </script></body></html>
            """.trimIndent()
            call.response.headers.append("Cache-Control", "no-store")
            call.respondText(html, ContentType.Text.Html)
        }

        private suspend fun completeReauthentication(
            call: ApplicationCall,
            oauth: GoogleOAuthStateCookie.State.Reauthenticate,
            code: String,
        ) {
            val identity = current.resolve(call) ?: return
            if (identity.userId != oauth.userId || identity.sessionId != oauth.sessionId) {
                callbackResponses.reauthentication(call, "failed")
                return
            }
            val result = reauthenticate.reauthenticate(
                ReauthenticateUseCase.Request.Google(
                    oauth.userId,
                    oauth.sessionId,
                    code,
                    oauth.codeVerifier,
                    redirectUri,
                ),
            )
            val status = if (result == ReauthenticateUseCase.Outcome.REAUTHENTICATED) "success" else "failed"
            callbackResponses.reauthentication(call, status)
        }

        private suspend fun completeLink(
            call: ApplicationCall,
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
                    oauth.userId,
                    oauth.sessionId,
                    oauth.actionProof,
                    code,
                    oauth.codeVerifier,
                    redirectUri,
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

        private suspend fun completeSignIn(call: ApplicationCall, outcome: SignInOutcome) {
            when (outcome) {
                is SignInOutcome.Issued -> {
                    responses.attachIssued(call, outcome)
                    call.respondRedirect("/auth/callback", permanent = false)
                }
                is SignInOutcome.NotIssued -> redirectForSignInReason(call, outcome)
            }
        }

        private suspend fun redirectForSignInReason(call: ApplicationCall, outcome: SignInOutcome.NotIssued) {
            when (val reason = outcome.reason) {
                is AuthenticationOutcome.RequiresSecondFactor -> call.respondRedirect(
                    "/mfa?pending_id=${reason.pendingId.value}" +
                        "&recommended_method=${reason.recommendedMethod.name}" +
                        "&methods=${reason.availableMethods.joinToString(",") { it.name }}",
                    permanent = false,
                )
                else -> callbackResponses.oauthFailure(call)
            }
        }

        private fun clearStateCookie(call: ApplicationCall) {
            call.response.cookies.append(
                Cookie(
                    cookieName,
                    "",
                    path = cookiePath,
                    secure = secure,
                    httpOnly = true,
                    extensions = mapOf("SameSite" to "Lax"),
                    maxAge = 0,
                ),
            )
        }

        private data class Attempt(val cookie: String?) {
            val actionProof: Boolean = cookie?.startsWith("proof.") == true
            val link: Boolean = cookie?.startsWith("link.") == true
            val reauthentication: Boolean = cookie?.startsWith("reauth.") == true
        }

        private data class CallbackContext(val oauth: GoogleOAuthStateCookie.State?, val attempt: Attempt)

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
            const val COOKIE = "tallyvane_google_oauth"
        }
    }
}
