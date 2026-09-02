package tallyvane.identity.application.port

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import tallyvane.identity.domain.secondfactor.EncryptedSecret
import tallyvane.identity.domain.secondfactor.totp.TotpEnrollment
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The behaviour every [TotpEnrollmentStore] must show, whatever stores it — the fake here, and
 * `TotpEnrollmentStoreOverExposed` against a real Postgres in `identity:infrastructure`
 * (ADR-046). [Subject] carries a [UserRepository] for the same reason
 * [CredentialRepositoryConformance] does: a real row has a foreign key to `identity.users`.
 */
public abstract class TotpEnrollmentStoreConformance : StringSpec() {
    protected abstract suspend fun fresh(): Subject

    public interface Subject {
        public val users: UserRepository
        public val enrollments: TotpEnrollmentStore
        public val transactions: TransactionRunner
    }

    init {
        "a saved enrollment is found back by user id, unchanged" {
            val subject = fresh()
            val userId = UserId(Uuid.random())
            val enrollment = testEnrollment(userId)

            subject.transactions.inTransaction {
                subject.users.insert(testUser(userId))
                subject.enrollments.save(enrollment)
                Verdict.Commit(Unit)
            }

            subject.transactions.inTransaction {
                Verdict.Commit(subject.enrollments.find(userId))
            } shouldBe enrollment
        }

        "a user id nobody enrolled has none" {
            val subject = fresh()
            val userId = UserId(Uuid.random())

            subject.transactions.inTransaction {
                subject.users.insert(testUser(userId))
                Verdict.Commit(Unit)
            }

            subject.transactions.inTransaction {
                Verdict.Commit(subject.enrollments.find(userId))
            }.shouldBeNull()
        }

        "confirming an enrollment rewrites the one row instead of colliding on a second insert" {
            val subject = fresh()
            val userId = UserId(Uuid.random())
            val started = testEnrollment(userId, active = false)
            val confirmed = started.copy(active = true)

            subject.transactions.inTransaction {
                subject.users.insert(testUser(userId))
                subject.enrollments.save(started)
                Verdict.Commit(Unit)
            }
            subject.transactions.inTransaction {
                subject.enrollments.save(confirmed)
                Verdict.Commit(Unit)
            }

            subject.transactions.inTransaction {
                Verdict.Commit(subject.enrollments.find(userId))
            } shouldBe confirmed
        }
    }

    private fun testUser(id: UserId): User = User(
        id = id,
        email = Email("person-${Uuid.random()}@example.com"),
        displayName = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        disabledAt = null,
    )

    private fun testEnrollment(userId: UserId, active: Boolean = false): TotpEnrollment = TotpEnrollment(
        userId = userId,
        secret = EncryptedSecret("ZmFrZS1jaXBoZXJ0ZXh0"),
        active = active,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}
