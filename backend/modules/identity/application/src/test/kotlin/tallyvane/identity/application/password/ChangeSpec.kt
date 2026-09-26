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
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ChangeSpec :
    StringSpec({
        "replaces a password only after the current password is verified" {
            val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000041"))
            val email = Email("person@example.test")
            val users = UserRepositoryFake().also {
                it.insert(User(userId, email, null, Instant.parse("2026-01-01T00:00:00Z"), null, emailVerified = true))
            }
            val credentials = CredentialRepositoryFake()
            credentials.save(userId, Credential.PasswordRecord(PasswordHash(Secret("hash:old passphrase long"))))
            val hasher = object : PasswordHasher {
                override fun hash(raw: Secret) = PasswordHash(Secret("hash:${raw.revealed()}"))
                override fun verify(raw: Secret, hash: PasswordHash) =
                    hash == PasswordHash(Secret("hash:${raw.revealed()}"))
            }
            val change = ChangePasswordUseCase.Change(users, credentials, hasher, TransactionRunnerFake())

            change.change(userId, Secret("wrong password long"), Secret("replacement passphrase long")) shouldBe false
            change.change(userId, Secret("old passphrase long"), Secret("too short")) shouldBe false
            credentials.findPasswordFor(userId)?.hash shouldBe PasswordHash(Secret("hash:old passphrase long"))

            change.change(userId, Secret("old passphrase long"), Secret("replacement passphrase long")) shouldBe true
            credentials.findPasswordFor(userId)?.hash shouldBe PasswordHash(Secret("hash:replacement passphrase long"))
        }
    })
