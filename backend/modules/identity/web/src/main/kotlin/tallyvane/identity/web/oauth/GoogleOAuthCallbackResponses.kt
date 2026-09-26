package tallyvane.identity.web.oauth

import io.ktor.server.response.respondRedirect

internal interface GoogleOAuthCallbackResponses {
    suspend fun providerError(call: io.ktor.server.application.ApplicationCall, error: String)
    suspend fun oauthFailure(call: io.ktor.server.application.ApplicationCall)
    suspend fun linkFailure(call: io.ktor.server.application.ApplicationCall, result: String)
    suspend fun reauthentication(call: io.ktor.server.application.ApplicationCall, result: String)

    class Redirector : GoogleOAuthCallbackResponses {
        override suspend fun providerError(call: io.ktor.server.application.ApplicationCall, error: String) {
            val result = if (error == "access_denied") "cancelled" else "oauth_failed"
            call.respondRedirect("/auth/google?error=$result", permanent = false)
        }

        override suspend fun oauthFailure(call: io.ktor.server.application.ApplicationCall) {
            call.respondRedirect("/auth/google?error=oauth_failed", permanent = false)
        }

        override suspend fun linkFailure(call: io.ktor.server.application.ApplicationCall, result: String) {
            call.respondRedirect("/account/security?google=$result", permanent = false)
        }

        override suspend fun reauthentication(call: io.ktor.server.application.ApplicationCall, result: String) {
            call.respondRedirect("/account/security?reauth=$result", permanent = false)
        }
    }
}
