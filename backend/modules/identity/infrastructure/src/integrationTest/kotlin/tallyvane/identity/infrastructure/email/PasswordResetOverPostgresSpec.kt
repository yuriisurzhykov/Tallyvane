package tallyvane.identity.infrastructure.email

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.email.EmailChallenges
import tallyvane.identity.application.email.ResetPasswordRequest
import tallyvane.identity.application.email.ResetPasswordUseCase
import tallyvane.identity.application.port.AuthenticationCodes
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.credential.PasswordHash
import tallyvane.identity.domain.email.EmailChallengePolicy
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.identity.infrastructure.persistence.CredentialRepositoryOverExposed
import tallyvane.identity.infrastructure.persistence.EmailChallengeStoreOverExposed
import tallyvane.identity.infrastructure.persistence.UserRepositoryOverExposed
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.Verdict
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PasswordResetOverPostgresSpec :
    StringSpec({
        "a persisted reset-purpose challenge replaces the password once" {
            PostgresPersistence(PostgresFixture.migrated()).use { persistence ->
                val now = Instant.parse("2026-09-24T12:00:00Z")
                val userId = UserId(Uuid.random())
                val email = Email("reset@example.test")
                val users = UserRepositoryOverExposed()
                val credentials = CredentialRepositoryOverExposed()
                val passwords = object : PasswordHasher {
                    override fun hash(raw: Secret) = PasswordHash(Secret("integration-hash:${raw.revealed()}"))
                    override fun verify(raw: Secret, hash: PasswordHash) = hash == this.hash(raw)
                }
                persistence.transactions.inTransaction {
                    users.insert(User(userId, email, null, now, null, emailVerified = true))
                    credentials.save(
                        userId,
                        Credential.PasswordRecord(passwords.hash(Secret("original password phrase"))),
                    )
                    Verdict.Commit(Unit)
                }
                val delivery = RecordingEmailDelivery()
                val codes = AuthenticationCodes.Hmac(Secret("test-only-code-pepper-32-bytes-long"))
                val challenges = EmailChallenges(
                    EmailChallengeStoreOverExposed(),
                    delivery,
                    codes,
                    persistence.transactions,
                    IdGenerator.Uuid7(),
                    ClockFake(now),
                    EmailChallengePolicy(),
                )
                val challenge = challenges.issue(email, EmailChallengePurpose.PASSWORD_RESET)!!
                val reset = ResetPasswordUseCase.Replace(
                    users,
                    credentials,
                    passwords,
                    challenges,
                    persistence.transactions,
                )

                reset.reset(
                    ResetPasswordRequest(challenge.id, email, delivery.code, Secret("replacement password phrase")),
                ) shouldBe
                    true
                val changed = persistence.transactions.inTransaction {
                    Verdict.Commit(credentials.findPasswordFor(userId))
                }!!
                passwords.verify(Secret("replacement password phrase"), changed.hash) shouldBe true
                passwords.verify(Secret("original password phrase"), changed.hash) shouldBe false
                challenges.verify(challenge.id, email, EmailChallengePurpose.PASSWORD_RESET, delivery.code) shouldBe
                    false
                persistence.transactions.inTransaction {
                    Verdict.Commit(users.findById(userId)?.emailVerified)
                } shouldBe
                    true
            }
        }
    })
