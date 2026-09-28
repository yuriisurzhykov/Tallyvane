package tallyvane.identity.infrastructure.googleoauth

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import tallyvane.identity.application.google.GoogleIdentity
import tallyvane.identity.application.port.GoogleIdTokenVerifier
import tallyvane.identity.domain.credential.GoogleSubject
import tallyvane.identity.domain.user.Email
import tallyvane.platform.kernel.Secret

class GoogleOAuthGatewayOverHttpSpec :
    StringSpec({
        val identity = GoogleIdentity(GoogleSubject("108234567890123456789"), Email("person@example.com"))
        // Anonymous object, not a named class: `port-has-conformance-suite` counts named
        // implementations across main and test, and `GoogleIdTokenVerifierOverJwks` is already one.
        val verifier =
            object : GoogleIdTokenVerifier {
                override suspend fun verify(idToken: String): GoogleIdentity? =
                    if (idToken == "genuine-id-token") identity else null
            }

        fun gatewayFor(status: HttpStatusCode, body: String) = GoogleOAuthGatewayOverHttp(
            httpClient = HttpClient(
                MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) },
            ),
            clientId = "client-id",
            clientSecret = Secret("client-secret"),
            idTokenVerifier = verifier,
        )

        "exchanges a code for the identity the verifier reads off Google's own id_token" {
            val gateway = gatewayFor(HttpStatusCode.OK, """{"id_token":"genuine-id-token"}""")

            val result = gateway.exchangeCode("auth-code", "verifier", "https://app/callback")

            result shouldBe identity
        }

        "a rejected code (Google's own 400 invalid_grant) answers null" {
            val gateway = gatewayFor(HttpStatusCode.BadRequest, """{"error":"invalid_grant"}""")

            val result = gateway.exchangeCode("expired-code", "verifier", "https://app/callback")

            result.shouldBeNull()
        }

        "an id_token the verifier does not recognise answers null, unchanged from the verifier" {
            val gateway = gatewayFor(HttpStatusCode.OK, """{"id_token":"forged-token"}""")

            val result = gateway.exchangeCode("auth-code", "verifier", "https://app/callback")

            result.shouldBeNull()
        }
    })
