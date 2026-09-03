package tallyvane.identity.application.port

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The behaviour every [UserRepository] must show, whatever stores it. Written once and
 * inherited by each implementation's own spec — the fake here, and
 * `UserRepositoryOverExposed` against a real Postgres in `identity:infrastructure` — so a
 * disagreement between them surfaces in a build rather than in production (ADR-046).
 *
 * Every case wraps its calls in [Subject.transactions] — a real adapter has no transaction of its
 * own to open, per [UserRepository]'s own KDoc, so a bare call outside one throws before this
 * suite ever gets to assert anything. Verified for real, not assumed:
 * `backend/playground/transactions/README.md`'s 2026-09-02 entry.
 */
public abstract class UserRepositoryConformance : StringSpec() {
    protected abstract suspend fun fresh(): Subject

    public interface Subject {
        public val users: UserRepository
        public val transactions: TransactionRunner
    }

    init {
        "a freshly inserted user is found back by id" {
            val subject = fresh()
            val user = testUser()

            subject.transactions.inTransaction {
                subject.users.insert(user)
                Verdict.Commit(Unit)
            }

            subject.transactions.inTransaction {
                Verdict.Commit(subject.users.findById(user.id))
            } shouldBe user
        }

        "a freshly inserted user is found back by email" {
            val subject = fresh()
            val user = testUser()

            subject.transactions.inTransaction {
                subject.users.insert(user)
                Verdict.Commit(Unit)
            }

            subject.transactions.inTransaction {
                Verdict.Commit(subject.users.findByEmail(user.email))
            } shouldBe user
        }

        "an id nobody was ever inserted under is not found" {
            val subject = fresh()

            subject.transactions.inTransaction {
                Verdict.Commit(subject.users.findById(UserId(Uuid.random())))
            }.shouldBeNull()
        }

        "inserting a second user under an email already taken reports EMAIL_TAKEN, not INSERTED" {
            val subject = fresh()
            val email = testEmail()

            subject.transactions.inTransaction {
                subject.users.insert(testUser(email = email))
                Verdict.Commit(Unit)
            }

            val outcome = subject.transactions.inTransaction {
                Verdict.Commit(subject.users.insert(testUser(email = email)))
            }
            outcome shouldBe UserRepository.InsertOutcome.EMAIL_TAKEN
        }

        "a refused insert leaves the first user's own row exactly as it was" {
            val subject = fresh()
            val email = testEmail()
            val first = testUser(email = email)

            subject.transactions.inTransaction {
                subject.users.insert(first)
                Verdict.Commit(Unit)
            }
            subject.transactions.inTransaction {
                subject.users.insert(testUser(email = email))
                Verdict.Commit(Unit)
            }

            subject.transactions.inTransaction {
                Verdict.Commit(subject.users.findByEmail(email))
            } shouldBe first
        }

        "a disabled account's disabledAt round-trips, not silently dropped as null" {
            val subject = fresh()
            val user = testUser(disabledAt = Instant.parse("2026-06-01T00:00:00Z"))

            subject.transactions.inTransaction {
                subject.users.insert(user)
                Verdict.Commit(Unit)
            }

            subject.transactions.inTransaction {
                Verdict.Commit(subject.users.findById(user.id))
            } shouldBe user
        }
    }

    private fun testEmail(): Email = Email("person-${Uuid.random()}@example.com")

    private fun testUser(
        id: UserId = UserId(Uuid.random()),
        email: Email = testEmail(),
        disabledAt: Instant? = null,
    ): User = User(
        id = id,
        email = email,
        displayName = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        disabledAt = disabledAt,
    )
}
