package tallyvane.identity.application.password

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.CredentialRepositoryFake
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.credential.PasswordHash
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret
import kotlin.time.Instant
import kotlin.uuid.Uuid

class VerifySpec :
    StringSpec({
        "verifies only the password credential of an active verified account" {
            val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000046"))
            val users = UserRepositoryFake().also {
                it.insert(
                    User(userId, Email("person@example.test"), null, Instant.parse("2026-01-01T00:00:00Z"), null, true),
                )
            }
            val credentials = CredentialRepositoryFake().also {
                it.save(userId, Credential.PasswordRecord(PasswordHash(Secret("correct password"))))
            }
            val passwords = object : PasswordHasher {
                override fun hash(raw: Secret) = PasswordHash(raw)
                override fun verify(raw: Secret, hash: PasswordHash) = raw == hash.encoded
            }
            val verify = VerifyPasswordUseCase.Verify(users, credentials, passwords)

            verify.verify(userId, Secret("correct password")) shouldBe true
            verify.verify(userId, Secret("wrong password")) shouldBe false
        }
    })
