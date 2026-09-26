package tallyvane.identity.application.googleoauth

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.CredentialRepositoryFake
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.credential.GoogleSubject
import tallyvane.identity.domain.credential.PasswordHash
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

class UnlinkSpec :
    StringSpec({
        "requires a valid password and preserves the last usable sign-in method" {
            val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000045"))
            val users = UserRepositoryFake().also {
                it.insert(
                    User(userId, Email("person@example.test"), null, Instant.parse("2026-01-01T00:00:00Z"), null, true),
                )
            }
            val credentials = CredentialRepositoryFake().also {
                it.save(userId, Credential.GoogleRecord(GoogleSubject("google-subject")))
            }
            val hasher = object : PasswordHasher {
                override fun hash(raw: Secret) = PasswordHash(raw)
                override fun verify(raw: Secret, hash: PasswordHash) = raw == hash.encoded
            }
            val unlink = UnlinkGoogleAccountUseCase.Unlink(users, credentials, hasher, TransactionRunnerFake())

            unlink.unlink(userId, Secret("wrong password")) shouldBe UnlinkGoogleAccountUseCase.Result.LastSignInMethod
            credentials.findGoogleFor(userId)?.subject shouldBe GoogleSubject("google-subject")

            credentials.save(userId, Credential.PasswordRecord(PasswordHash(Secret("correct password"))))
            unlink.unlink(userId, Secret("wrong password")) shouldBe UnlinkGoogleAccountUseCase.Result.InvalidCredential
            credentials.findGoogleFor(userId)?.subject shouldBe GoogleSubject("google-subject")

            unlink.unlink(userId, Secret("correct password")) shouldBe UnlinkGoogleAccountUseCase.Result.Unlinked
            credentials.findGoogleFor(userId) shouldBe null
        }
    })
