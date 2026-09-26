package tallyvane.identity.infrastructure.email

import io.kotest.assertions.throwables.shouldThrow
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
import tallyvane.identity.infrastructure.persistence.EmailMfaEnrollmentStoreOverExposed
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

private const val MAX_FAILED_GUESSES = 5
private const val PARALLEL_REQUESTS = 8
private const val BACKUP_CODE_COUNT = 10

class EmailChallengesOverPostgresSpec :
    StringSpec({
        val now = Instant.parse("2026-09-24T00:00:00Z")
        val email = Email("recipient@example.com")
        val codes = AuthenticationCodes.Hmac(Secret("test-only-code-pepper-32-bytes-long"))

        fun challenges(persistence: PostgresPersistence, delivery: EmailDelivery, at: Instant = now) = EmailChallenges(
            EmailChallengeStoreOverExposed(),
            delivery,
            codes,
            persistence.transactions,
            IdGenerator.Uuid7(),
            ClockFake(at),
            EmailChallengePolicy(),
        )

        "purpose and pending-operation binding reject cross-use without consuming the legitimate challenge" {
            PostgresPersistence(PostgresFixture.migrated()).use { persistence ->
                val delivery = RecordingEmailDelivery()
                val subject = challenges(persistence, delivery)
                val challenge = subject.issue(email, EmailChallengePurpose.MFA, "pending-a").shouldNotBeNull()
                subject.verify(
                    challenge.id,
                    email,
                    EmailChallengePurpose.PASSWORD_RESET,
                    delivery.code,
                    "pending-a",
                ) shouldBe
                    false
                subject.verify(challenge.id, email, EmailChallengePurpose.MFA, delivery.code, "pending-b") shouldBe
                    false
                subject.verify(challenge.id, email, EmailChallengePurpose.MFA, delivery.code, "pending-a") shouldBe true
                subject.verify(challenge.id, email, EmailChallengePurpose.MFA, delivery.code, "pending-a") shouldBe
                    false
            }
        }

        "resend obeys sixty seconds and replaces the old code and challenge id" {
            PostgresPersistence(PostgresFixture.migrated()).use { persistence ->
                val delivery = RecordingEmailDelivery()
                val first = challenges(
                    persistence,
                    delivery,
                ).issue(email, EmailChallengePurpose.EMAIL_LOGIN).shouldNotBeNull()
                val firstCode = delivery.code
                challenges(
                    persistence,
                    delivery,
                    now + 59.seconds,
                ).issue(email, EmailChallengePurpose.EMAIL_LOGIN).shouldBeNull()
                val next = challenges(persistence, delivery, now + 60.seconds)
                val second = next.issue(email, EmailChallengePurpose.EMAIL_LOGIN).shouldNotBeNull()
                next.verify(first.id, email, EmailChallengePurpose.EMAIL_LOGIN, firstCode) shouldBe false
                next.verify(second.id, email, EmailChallengePurpose.EMAIL_LOGIN, delivery.code) shouldBe true
            }
        }

        "SMTP failure revokes the undelivered code and a later resend can recover" {
            PostgresPersistence(PostgresFixture.migrated()).use { persistence ->
                var fail = true
                var deliveredCode: Secret? = null
                val delivery = object : EmailDelivery {
                    override suspend fun sendCode(email: Email, purpose: EmailChallengePurpose, code: Secret) {
                        if (fail) {
                            fail = false
                            throw IllegalStateException("mail transport unavailable")
                        }
                        deliveredCode = code
                    }
                }
                val firstRequest = challenges(persistence, delivery)
                shouldThrow<IllegalStateException> {
                    firstRequest.issue(email, EmailChallengePurpose.REGISTRATION, "user-id")
                }

                val recovered = challenges(persistence, delivery, now + 60.seconds)
                    .issue(email, EmailChallengePurpose.REGISTRATION, "user-id").shouldNotBeNull()
                deliveredCode.shouldNotBeNull()
                challenges(persistence, delivery, now + 60.seconds)
                    .verify(recovered.id, email, EmailChallengePurpose.REGISTRATION, deliveredCode, "user-id") shouldBe
                    true
            }
        }

        "expiry rejects exactly at ten minutes" {
            PostgresPersistence(PostgresFixture.migrated()).use { persistence ->
                val delivery = RecordingEmailDelivery()
                val challenge = challenges(
                    persistence,
                    delivery,
                ).issue(email, EmailChallengePurpose.REGISTRATION).shouldNotBeNull()
                challenges(persistence, delivery, now + 10.minutes)
                    .verify(challenge.id, email, EmailChallengePurpose.REGISTRATION, delivery.code) shouldBe false
            }
        }

        "five bad guesses exhaust a challenge and cannot be undone by a correct sixth guess" {
            PostgresPersistence(PostgresFixture.migrated()).use { persistence ->
                val delivery = RecordingEmailDelivery()
                val subject = challenges(persistence, delivery)
                val challenge = subject.issue(email, EmailChallengePurpose.PASSWORD_RESET).shouldNotBeNull()
                repeat(MAX_FAILED_GUESSES) {
                    subject.verify(challenge.id, email, EmailChallengePurpose.PASSWORD_RESET, Secret("wrong")) shouldBe
                        false
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
                    List(PARALLEL_REQUESTS) {
                        async { subject.verify(challenge.id, email, EmailChallengePurpose.EMAIL_LOGIN, delivery.code) }
                    }.awaitAll()
                }
                results.count { it } shouldBe 1
            }
        }

        "simultaneous first issuance has one winner and sends only one message" {
            PostgresPersistence(PostgresFixture.migrated()).use { persistence ->
                val delivery = RecordingEmailDelivery()
                val subject = challenges(persistence, delivery)
                val results = coroutineScope {
                    List(PARALLEL_REQUESTS) {
                        async { subject.issue(email, EmailChallengePurpose.REGISTRATION) }
                    }.awaitAll()
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
                val subject = BackupCodes(BackupCodeStoreOverExposed(), codes)
                val original = persistence.transactions.inTransaction { Verdict.Commit(subject.issue(userId)) }
                original.size shouldBe BACKUP_CODE_COUNT
                persistence.transactions.inTransaction { Verdict.Commit(subject.hasAny(userId)) } shouldBe true
                val results =
                    coroutineScope {
                        List(PARALLEL_REQUESTS) {
                            async {
                                persistence.transactions.inTransaction {
                                    Verdict.Commit(subject.consume(userId, original.first()))
                                }
                            }
                        }.awaitAll()
                    }
                results.count { it } shouldBe 1
                val replacement = persistence.transactions.inTransaction { Verdict.Commit(subject.issue(userId)) }
                original.forEach { code ->
                    persistence.transactions.inTransaction { Verdict.Commit(subject.consume(userId, code)) } shouldBe false
                }
                persistence.transactions.inTransaction { Verdict.Commit(subject.hasAny(userId)) } shouldBe true
                replacement.forEach { code ->
                    persistence.transactions.inTransaction { Verdict.Commit(subject.consume(userId, code)) } shouldBe true
                }
                persistence.transactions.inTransaction { Verdict.Commit(subject.hasAny(userId)) } shouldBe false
            }
        }

        "email MFA enrollment is persisted only after explicit confirmation" {
            PostgresPersistence(PostgresFixture.migrated()).use { persistence ->
                val userId = UserId(Uuid.random())
                val store = EmailMfaEnrollmentStoreOverExposed()
                persistence.transactions.inTransaction {
                    UserRepositoryOverExposed().insert(User(userId, email, null, now, null))
                    Verdict.Commit(Unit)
                }
                persistence.transactions.inTransaction { Verdict.Commit(store.isEnrolled(userId)) } shouldBe false
                persistence.transactions.inTransaction {
                    store.enroll(userId)
                    Verdict.Commit(store.isEnrolled(userId))
                } shouldBe true
                persistence.transactions.inTransaction {
                    store.unenroll(userId)
                    Verdict.Commit(store.isEnrolled(userId))
                } shouldBe false
            }
        }
    })
