package tallyvane.identity.application.googleoauth

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.google.GoogleIdentity
import tallyvane.identity.application.port.CredentialRepositoryFake
import tallyvane.identity.application.port.GoogleOAuthGateway
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.credential.GoogleSubject
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

class LinkSpec :
    StringSpec({
        "links only an explicitly authorized Google identity and refuses a subject owned by another account" {
            val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000042"))
            val otherId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000043"))
            val users = UserRepositoryFake().also {
                it.insert(
                    User(userId, Email("person@example.test"), null, Instant.parse("2026-01-01T00:00:00Z"), null, true),
                )
                it.insert(
                    User(otherId, Email("other@example.test"), null, Instant.parse("2026-01-01T00:00:00Z"), null, true),
                )
            }
            val credentials = CredentialRepositoryFake().also {
                it.save(otherId, Credential.GoogleRecord(GoogleSubject("already-linked")))
            }
            val gateway = object : GoogleOAuthGateway {
                override suspend fun exchangeCode(code: String, codeVerifier: String, redirectUri: String) =
                    if (code == "invalid") null else GoogleIdentity(GoogleSubject(code), Email("google@example.test"))
            }
            val useCase = LinkGoogleAccountUseCase.Link(gateway, users, credentials, TransactionRunnerFake())

            useCase.link(
                LinkGoogleAccountUseCase.Request(
                    userId,
                    "invalid",
                    "verifier",
                    "https://app/callback",
                ),
            ) shouldBe
                LinkGoogleAccountUseCase.Result.InvalidCredential
            credentials.findGoogleFor(userId) shouldBe null

            useCase.link(
                LinkGoogleAccountUseCase.Request(
                    userId,
                    "already-linked",
                    "verifier",
                    "https://app/callback",
                ),
            ) shouldBe
                LinkGoogleAccountUseCase.Result.SubjectAlreadyLinked
            credentials.findGoogleFor(userId) shouldBe null

            useCase.link(
                LinkGoogleAccountUseCase.Request(
                    userId,
                    "new-subject",
                    "verifier",
                    "https://app/callback",
                ),
            ) shouldBe
                LinkGoogleAccountUseCase.Result.Linked
            credentials.findGoogleFor(userId)?.subject shouldBe GoogleSubject("new-subject")
        }

        "does not link an invalid OAuth code" {
            val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000044"))
            val users = UserRepositoryFake().also {
                it.insert(
                    User(userId, Email("person@example.test"), null, Instant.parse("2026-01-01T00:00:00Z"), null, true),
                )
            }
            val credentials = CredentialRepositoryFake()
            val gateway = object : GoogleOAuthGateway {
                override suspend fun exchangeCode(
                    code: String,
                    codeVerifier: String,
                    redirectUri: String,
                ): GoogleIdentity? = null
            }
            val useCase = LinkGoogleAccountUseCase.Link(gateway, users, credentials, TransactionRunnerFake())
            val request = LinkGoogleAccountUseCase.Request(userId, "code", "verifier", "https://app/callback")

            useCase.link(request) shouldBe LinkGoogleAccountUseCase.Result.InvalidCredential
            credentials.findGoogleFor(userId) shouldBe null
        }
    })
