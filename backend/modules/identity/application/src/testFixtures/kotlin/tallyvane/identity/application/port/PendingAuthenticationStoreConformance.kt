package tallyvane.identity.application.port

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import tallyvane.identity.domain.secondfactor.PendingAuthentication
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The behaviour every [PendingAuthenticationStore] must show, whatever stores it — the fake here,
 * and `PendingAuthenticationStoreOverExposed` against a real Postgres in `identity:infrastructure`
 * (ADR-046). [Subject] carries a [UserRepository] for the same reason
 * [CredentialRepositoryConformance] does: a real row has a foreign key to `identity.users`.
 */
public abstract class PendingAuthenticationStoreConformance : StringSpec() {
    protected abstract suspend fun fresh(): Subject

    public interface Subject {
        public val users: UserRepository
        public val pendingAuthentications: PendingAuthenticationStore
        public val transactions: TransactionRunner
    }

    init {
        "a saved pending authentication is found back by its own id, unchanged" {
            val subject = fresh()
            val userId = UserId(Uuid.random())
            val pending = testPending(userId)

            subject.transactions.inTransaction {
                subject.users.insert(testUser(userId))
                subject.pendingAuthentications.save(pending)
                Verdict.Commit(Unit)
            }

            subject.transactions.inTransaction {
                Verdict.Commit(subject.pendingAuthentications.find(pending.id))
            } shouldBe pending
        }

        "an id nobody ever saved a pending authentication under is not found" {
            val subject = fresh()

            subject.transactions.inTransaction {
                Verdict.Commit(subject.pendingAuthentications.find(PendingAuthenticationId(Uuid.random())))
            }.shouldBeNull()
        }

        "a deleted pending authentication cannot be found again" {
            val subject = fresh()
            val userId = UserId(Uuid.random())
            val pending = testPending(userId)

            subject.transactions.inTransaction {
                subject.users.insert(testUser(userId))
                subject.pendingAuthentications.save(pending)
                Verdict.Commit(Unit)
            }
            subject.transactions.inTransaction {
                subject.pendingAuthentications.delete(pending.id)
                Verdict.Commit(Unit)
            }

            subject.transactions.inTransaction {
                Verdict.Commit(subject.pendingAuthentications.find(pending.id))
            }.shouldBeNull()
        }
    }

    private fun testUser(id: UserId): User = User(
        id = id,
        email = Email("person-${Uuid.random()}@example.com"),
        displayName = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        disabledAt = null,
    )

    private fun testPending(userId: UserId): PendingAuthentication {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        return PendingAuthentication(
            id = PendingAuthenticationId(Uuid.random()),
            userId = userId,
            device = DeviceLabel("Chrome on MacBook"),
            availableMethods = setOf(SecondFactorKind.TOTP),
            createdAt = now,
            expiresAt = now + 5.minutes,
        )
    }
}
