package tallyvane.identity.web.oauth

import io.ktor.http.*
import io.ktor.server.response.*
import tallyvane.identity.application.SignInOutcome
import tallyvane.identity.application.googleoauth.SignInWithGoogleOAuthRequest
import tallyvane.identity.application.googleoauth.SignInWithGoogleOAuthUseCase
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.web.login.SignInResponses

/** OAuth Authorization Code + PKCE entry and callback. State and verifier are short-lived, HttpOnly cookies. */
internal interface GoogleOAuthHandler {
    suspend fun start(call: io.ktor.server.application.ApplicationCall)
    suspend fun callback(call: io.ktor.server.application.ApplicationCall)

    class Redirector(
        private val clientId: String,
        private val redirectUri: String,
        private val signIn: SignInWithGoogleOAuthUseCase,
        private val responses: SignInResponses,
        private val secure: Boolean,
    ) : GoogleOAuthHandler {
        override suspend fun start(call: io.ktor.server.application.ApplicationCall) {
            val challenge = PkceChallenge.Generator().generate()
            call.response.cookies.append(
                Cookie(
                    COOKIE, "${challenge.state}.${challenge.verifier}",
                    path = COOKIE_PATH, secure = secure, httpOnly = true,
                    extensions = mapOf("SameSite" to "Lax"), maxAge = 300,
                ),
            )
            val location = URLBuilder(AUTHORIZATION_ENDPOINT).apply {
                parameters.append("client_id", clientId)
                parameters.append("redirect_uri", redirectUri)
                parameters.append("response_type", "code")
                parameters.append("scope", "openid email profile")
                parameters.append("state", challenge.state)
                parameters.append("code_challenge", challenge.challenge)
                parameters.append("code_challenge_method", "S256")
            }.buildString()
            call.respondRedirect(location, permanent = false)
        }

        override suspend fun callback(call: io.ktor.server.application.ApplicationCall) {
            val cookie = call.request.cookies[COOKIE]
            val state = call.request.queryParameters["state"]
            val code = call.request.queryParameters["code"]
            val providerError = call.request.queryParameters["error"]
            call.response.cookies.append(
                Cookie(
                    COOKIE, "", path = COOKIE_PATH, secure = secure, httpOnly = true,
                    extensions = mapOf("SameSite" to "Lax"), maxAge = 0,
                ),
            )
            val parts = cookie?.split('.', limit = 2)
            if (providerError != null) {
                val result = if (providerError == "access_denied") "cancelled" else "oauth_failed"
                call.respondRedirect("/auth/google?error=$result", permanent = false)
                return
            }
            if (parts == null || parts.size != 2 || state == null || code == null || !constantTimeEquals(
                    parts[0],
                    state,
                )
            ) {
                call.respondRedirect("/auth/google?error=oauth_failed", permanent = false)
                return
            }
            when (val outcome =
                signIn.signIn(SignInWithGoogleOAuthRequest(code, parts[1], redirectUri, DeviceLabel("Browser")))) {
                is SignInOutcome.Issued -> {
                    responses.attachIssued(call, outcome)
                    call.respondRedirect("/today", permanent = false)
                }

                is SignInOutcome.NotIssued -> {
                    when (val reason = outcome.reason) {
                        is tallyvane.identity.domain.outcome.AuthenticationOutcome.RequiresSecondFactor ->
                            call.respondRedirect(
                                "/mfa?pending_id=${reason.pendingId.value}&methods=${
                                    reason.availableMethods.joinToString(
                                        ",",
                                    ) { it.name }
                                }",
                                permanent = false,
                            )

                        else                                                                            -> call.respondRedirect(
                            "/auth/google?error=oauth_failed",
                            permanent = false,
                        )
                    }
                }
            }
        }

        private fun constantTimeEquals(left: String, right: String): Boolean =
            java.security.MessageDigest.isEqual(left.toByteArray(Charsets.UTF_8), right.toByteArray(Charsets.UTF_8))

        private companion object {
            const val AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
            const val COOKIE = "tallyvane_google_oauth"
            const val COOKIE_PATH = "/api/v1/auth/google/oauth"
        }
    }
}
