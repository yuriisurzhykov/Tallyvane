package tallyvane.identity.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import tallyvane.identity.application.email.RequestEmailSignInCodeUseCase
import tallyvane.identity.application.email.RequestPasswordResetUseCase
import tallyvane.identity.domain.email.EmailChallenge
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.user.Email
import tallyvane.identity.web.login.AuthenticationProblems
import tallyvane.identity.web.login.RequestEmailSignInCodeHandler
import tallyvane.identity.web.password.RequestPasswordResetHandler
import tallyvane.identity.web.routing.AuthRoutes
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.platform.http.Api
import tallyvane.platform.http.TraceHeader
import tallyvane.platform.http.problems.FailureTranslator
import tallyvane.platform.kernel.IdGenerator
import kotlin.time.Instant
import kotlin.uuid.Uuid

class EmailChallengeResponsesSpec :
    StringSpec({
        "email sign-in code response contains the UUID challenge_id" {
            val challenge = EmailChallenge(
                id = Uuid.parse("00000000-0000-7000-8000-000000000001"),
                email = Email("taylor@example.test"),
                purpose = EmailChallengePurpose.EMAIL_LOGIN,
                binding = "test-binding",
                expiresAt = Instant.parse("2026-01-01T00:00:00Z"),
            )
            val request = object : RequestEmailSignInCodeUseCase {
                override suspend fun request(email: Email) = challenge
            }

            testApplication {
                application { installEmailChallengeRoutes(this, request) }
                val response = client.post("/api/v1/auth/login/email/code") {
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"email":"taylor@example.test"}""")
                }

                response.status shouldBe HttpStatusCode.Accepted
                response.bodyAsText() shouldBe """{"challenge_id":"00000000-0000-7000-8000-000000000001"}"""
            }
        }

        "password reset code response contains the UUID challenge_id" {
            val request = object : RequestPasswordResetUseCase {
                override suspend fun request(email: Email) = Uuid.parse("00000000-0000-7000-8000-000000000002")
            }

            testApplication {
                application { installEmailChallengeRoutes(this, passwordReset = request) }
                val response = client.post("/api/v1/auth/password/forgot") {
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"email":"taylor@example.test"}""")
                }

                response.status shouldBe HttpStatusCode.Accepted
                response.bodyAsText() shouldBe """{"challenge_id":"00000000-0000-7000-8000-000000000002"}"""
            }
        }
    })

private fun installEmailChallengeRoutes(
    application: io.ktor.server.application.Application,
    emailSignIn: RequestEmailSignInCodeUseCase = unusedEmailSignIn(),
    passwordReset: RequestPasswordResetUseCase = unusedPasswordReset(),
) {
    val handlers = listOf(
        RequestEmailSignInCodeHandler(emailSignIn, AuthenticationProblems(), RequestValidationProblems()),
        RequestPasswordResetHandler(passwordReset, RequestValidationProblems(), AuthenticationProblems()),
    )
    Api(
        listOf(AuthRoutes.Installation(handlers, false)),
        FailureTranslator.Chained(emptyList()),
        TraceHeader(IdGenerator.Uuid7()),
    ).install(application)
}

private fun unusedEmailSignIn() = object : RequestEmailSignInCodeUseCase {
    override suspend fun request(email: Email): EmailChallenge? = null
}

private fun unusedPasswordReset() = object : RequestPasswordResetUseCase {
    override suspend fun request(email: Email): Uuid? = null
}
