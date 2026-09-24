package tallyvane.identity.infrastructure.email

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tallyvane.identity.application.email.BackupCodes
import tallyvane.identity.application.email.EmailChallenges
import tallyvane.identity.application.port.AuthenticationCodes
import tallyvane.identity.application.port.EmailDelivery
import tallyvane.identity.domain.email.EmailChallengePolicy
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.identity.infrastructure.persistence.BackupCodeStoreOverExposed
import tallyvane.identity.infrastructure.persistence.EmailChallengeStoreOverExposed
import tallyvane.identity.infrastructure.persistence.UserRepositoryOverExposed
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGenerator
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.Verdict
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class EmailChallengesOverPostgresSpec : StringSpec({
    val now = Instant.parse("2026-09-24T00:00:00Z")
    val email = Email("recipient@example.com")
    val codes = AuthenticationCodes.Hmac(Secret("test-only-code-pepper-32-bytes-long"))

    fun challenges(persistence: PostgresPersistence, delivery: EmailDelivery, at: Instant = now) = EmailChallenges(
        EmailChallengeStoreOverExposed(), delivery, codes, persistence.transactions, IdGenerator.Uuid7(),
        ClockFake(at), EmailChallengePolicy(),
    )

    "purpose and pending-operation binding reject cross-use without consuming the legitimate challenge" {
        PostgresPersistence(PostgresFixture.migrated()).use { persistence ->
            val delivery = RecordingEmailDelivery()
            val subject = challenges(persistence, delivery)
            val challenge = subject.issue(email, EmailChallengePurpose.MFA, "pending-a").shouldNotBeNull()
            subject.verify(challenge.id, email, EmailChallengePurpose.PASSWORD_RESET, delivery.code, "pending-a") shouldBe false
            subject.verify(challenge.id, email, EmailChallengePurpose.MFA, delivery.code, "pending-b") shouldBe false
            subject.verify(challenge.id, email, EmailChallengePurpose.MFA, delivery.code, "pending-a") shouldBe true
            subject.verify(challenge.id, email, EmailChallengePurpose.MFA, delivery.code, "pending-a") shouldBe false
        }
    }

    "resend obeys sixty seconds and replaces the old code and challenge id" {
        PostgresPersistence(PostgresFixture.migrated()).use { persistence ->
            val delivery = RecordingEmailDelivery()
            val first = challenges(persistence, delivery).issue(email, EmailChallengePurpose.EMAIL_LOGIN).shouldNotBeNull()
            val firstCode = delivery.code
            challenges(persistence, delivery, now + 59.seconds).issue(email, EmailChallengePurpose.EMAIL_LOGIN).shouldBeNull()
            val next = challenges(persistence, delivery, now + 60.seconds)
            val second = next.issue(email, EmailChallengePurpose.EMAIL_LOGIN).shouldNotBeNull()
            next.verify(first.id, email, EmailChallengePurpose.EMAIL_LOGIN, firstCode) shouldBe false
            next.verify(second.id, email, EmailChallengePurpose.EMAIL_LOGIN, delivery.code) shouldBe true
        }
    }

    "expiry rejects exactly at ten minutes" {
        PostgresPersistence(PostgresFixture.migrated()).use { persistence ->
            val delivery = RecordingEmailDelivery()
            val challenge = challenges(persistence, delivery).issue(email, EmailChallengePurpose.REGISTRATION).shouldNotBeNull()
            challenges(persistence, delivery, now + 10.minutes)
                .verify(challenge.id, email, EmailChallengePurpose.REGISTRATION, delivery.code) shouldBe false
        }
    }

    "five bad guesses exhaust a challenge and cannot be undone by a correct sixth guess" {
        PostgresPersistence(PostgresFixture.migrated()).use { persistence ->
            val delivery = RecordingEmailDelivery()
            val subject = challenges(persistence, delivery)
            val challenge = subject.issue(email, EmailChallengePurpose.PASSWORD_RESET).shouldNotBeNull()
            repeat(5) {
                subject.verify(challenge.id, email, EmailChallengePurpose.PASSWORD_RESET, Secret("wrong")) shouldBe false
            }
            subject.verify(challenge.id, email, EmailChallengePurpose.PASSWORD_RESET, delivery.code) shouldBe false
        }
    }

    "simultaneous valid verification has exactly one winner" {
        PostgresPersistence(PostgresFixture.migrated()).use { persistence ->
            val delivery = RecordingEmailDelivery()
            val subject = challenges(persistence, delivery)
            val challenge = subject.issue(email, EmailChallengePurpose.EMAIL_LOGIN).shouldNotBeNull()
            val results = coroutineScope {
                List(8) { async { subject.verify(challenge.id, email, EmailChallengePurpose.EMAIL_LOGIN, delivery.code) } }.awaitAll()
            }
            results.count { it } shouldBe 1
        }
    }

    "simultaneous first issuance has one winner and sends only one message" {
        PostgresPersistence(PostgresFixture.migrated()).use { persistence ->
            val delivery = RecordingEmailDelivery()
            val subject = challenges(persistence, delivery)
            val results = coroutineScope {
                List(8) { async { subject.issue(email, EmailChallengePurpose.REGISTRATION) } }.awaitAll()
            }
            results.count { it != null } shouldBe 1
            delivery.sent shouldBe 1
        }
    }

    "backup codes are single use and reissue revokes the entire previous generation" {
        PostgresPersistence(PostgresFixture.migrated()).use { persistence ->
            val userId = UserId(Uuid.random())
            persistence.transactions.inTransaction {
                UserRepositoryOverExposed().insert(User(userId, email, null, now, null))
                Verdict.Commit(Unit)
            }
            val subject = BackupCodes(BackupCodeStoreOverExposed(), codes, persistence.transactions)
            val original = subject.issue(userId)
            original.size shouldBe 10
            val results = coroutineScope { List(8) { async { subject.consume(userId, original.first()) } }.awaitAll() }
            results.count { it } shouldBe 1
            val replacement = subject.issue(userId)
            original.forEach { subject.consume(userId, it) shouldBe false }
            replacement.forEach { subject.consume(userId, it) shouldBe true }
        }
    }
})
