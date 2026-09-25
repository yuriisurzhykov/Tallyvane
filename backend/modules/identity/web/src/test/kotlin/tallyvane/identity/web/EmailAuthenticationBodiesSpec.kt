package tallyvane.identity.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.testing.testApplication
import tallyvane.identity.application.SignInOutcome
import tallyvane.identity.application.email.RequestEmailSignInCodeUseCase
import tallyvane.identity.application.email.SignInWithEmailCodeRequest
import tallyvane.identity.application.email.SignInWithEmailCodeUseCase
import tallyvane.identity.application.secondfactor.RequestEmailMfaCodeUseCase
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.identity.domain.user.Email
import tallyvane.identity.web.login.AuthenticationFailure
import tallyvane.identity.web.login.AuthenticationProblems
import tallyvane.identity.web.login.EmailSignInHandler
import tallyvane.identity.web.login.RequestEmailSignInCodeHandler
import tallyvane.identity.web.login.SignInResponses
import tallyvane.identity.web.mfa.RequestEmailMfaCodeHandler
import tallyvane.identity.web.mfa.SecondFactorProblems
import tallyvane.identity.web.routing.AuthRoutes
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.platform.http.Api
import tallyvane.platform.http.TraceHeader
import tallyvane.platform.http.problems.FailureTranslator
import tallyvane.platform.kernel.IdGenerator
import kotlin.uuid.Uuid

class EmailAuthenticationBodiesSpec : StringSpec({
    "email sign-in code request accepts the API JSON body" {
        var requestedEmail: Email? = null
        val request = object : RequestEmailSignInCodeUseCase {
            override suspend fun request(email: Email) = null.also { requestedEmail = email }
        }

        testApplication {
            application { installAuthRoutes(this, request, unusedSignIn(), unusedMfa()) }
            val response = client.post("/api/v1/auth/login/email/code") {
                header(HttpHeaders.ContentType, "application/json")
                setBody("""{"email":"taylor@example.test"}""")
            }

            response.status shouldBe HttpStatusCode.TooManyRequests
            requestedEmail shouldBe Email("taylor@example.test")
        }
    }

    "email sign-in verification accepts snake case API fields" {
        var submitted: SignInWithEmailCodeRequest? = null
        val signIn = object : SignInWithEmailCodeUseCase {
            override suspend fun signIn(request: SignInWithEmailCodeRequest): SignInOutcome {
                submitted = request
                return SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)
            }
        }

        testApplication {
            application { installAuthRoutes(this, unusedEmailRequest(), signIn, unusedMfa()) }
            val response = client.post("/api/v1/auth/login/email/verify") {
                header(HttpHeaders.ContentType, "application/json")
                setBody(
                    """{"challenge_id":"00000000-0000-7000-8000-000000000001","email":"taylor@example.test","code":"123456","device":"Browser"}""",
                )
            }

            response.status shouldBe HttpStatusCode.NoContent
            submitted?.challengeId.toString() shouldBe "00000000-0000-7000-8000-000000000001"
            submitted?.email shouldBe Email("taylor@example.test")
        }
    }

    "email MFA code request accepts pending_id" {
        var requestedPendingId: PendingAuthenticationId? = null
        val request = object : RequestEmailMfaCodeUseCase {
            override suspend fun request(pendingId: PendingAuthenticationId) =
                null.also { requestedPendingId = pendingId }
        }

        testApplication {
            application { installAuthRoutes(this, unusedEmailRequest(), unusedSignIn(), request) }
            val response = client.post("/api/v1/auth/mfa/email/request") {
                header(HttpHeaders.ContentType, "application/json")
                setBody("""{"pending_id":"00000000-0000-7000-8000-000000000001"}""")
            }

            response.status shouldBe HttpStatusCode.TooManyRequests
            requestedPendingId?.value.toString() shouldBe "00000000-0000-7000-8000-000000000001"
        }
    }
})

private fun installAuthRoutes(
    application: Application,
    emailRequest: RequestEmailSignInCodeUseCase,
    emailSignIn: SignInWithEmailCodeUseCase,
    mfaRequest: RequestEmailMfaCodeUseCase,
): Unit {
    val handlers = listOf(
        RequestEmailSignInCodeHandler(emailRequest, AuthenticationProblems(), RequestValidationProblems()),
        EmailSignInHandler(emailSignIn, NoContentSignInResponses, AuthenticationProblems(), RequestValidationProblems()),
        RequestEmailMfaCodeHandler(mfaRequest, SecondFactorProblems(), RequestValidationProblems()),
    )
    Api(
        listOf(AuthRoutes.Installation(handlers, false)),
        FailureTranslator.Chained(emptyList()),
        TraceHeader(IdGenerator.Uuid7()),
    ).install(application)
}

private object NoContentSignInResponses : SignInResponses {
    override fun attachIssued(call: io.ktor.server.application.ApplicationCall, outcome: SignInOutcome.Issued) = Unit

    override suspend fun respond(
        call: io.ktor.server.application.ApplicationCall,
        outcome: SignInOutcome,
        problems: tallyvane.platform.http.problems.Problems<AuthenticationFailure>,
    ) {
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun unusedEmailRequest() = object : RequestEmailSignInCodeUseCase {
    override suspend fun request(email: Email) = null
}

private fun unusedSignIn() = object : SignInWithEmailCodeUseCase {
    override suspend fun signIn(request: SignInWithEmailCodeRequest): SignInOutcome =
        SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)
}

private fun unusedMfa() = object : RequestEmailMfaCodeUseCase {
    override suspend fun request(pendingId: PendingAuthenticationId): Uuid? = null
}
