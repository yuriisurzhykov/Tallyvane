package tallyvane.identity.application.port

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.credential.GoogleSubject
import tallyvane.identity.domain.credential.PasswordHash
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The behaviour every [CredentialRepository] must show, whatever stores it — the fake here, and
 * `CredentialRepositoryOverExposed` against a real Postgres in `identity:infrastructure`
 * (ADR-046). See [UserRepositoryConformance] for why every case wraps calls in
 * [Subject.transactions].
 *
 * [Subject] carries a [UserRepository] too, not only the port under test: a real adapter's
 * `password_credentials`/`google_credentials` rows have a foreign key to `identity.users`, which
 * the fake does not enforce — a case that only inserted a credential, with no user behind it,
 * would pass against the fake and fail against Postgres for a reason this suite exists to catch.
 */
public abstract class CredentialRepositoryConformance : StringSpec() {
    protected abstract suspend fun fresh(): Subject

    public interface Subject {
        public val users: UserRepository
        public val credentials: CredentialRepository
        public val transactions: TransactionRunner
    }

    init {
        "a saved password credential is found back by user id" {
            val subject = fresh()
            val userId = UserId(Uuid.random())
            val credential = Credential.PasswordRecord(PasswordHash(Secret("argon2id\$encoded\$fixture")))

            subject.transactions.inTransaction {
                subject.users.insert(testUser(userId))
                subject.credentials.save(userId, credential)
                Verdict.Commit(Unit)
            }

            subject.transactions.inTransaction {
                Verdict.Commit(subject.credentials.findPasswordFor(userId))
            } shouldBe credential
        }

        "a user id nobody saved a password for has none" {
            val subject = fresh()
            val userId = UserId(Uuid.random())

            subject.transactions.inTransaction {
                subject.users.insert(testUser(userId))
                Verdict.Commit(Unit)
            }

            subject.transactions.inTransaction {
                Verdict.Commit(subject.credentials.findPasswordFor(userId))
            }.shouldBeNull()
        }

        "a saved Google credential is found back by its own subject" {
            val subject = fresh()
            val userId = UserId(Uuid.random())
            val subjectId = GoogleSubject(Uuid.random().toString())

            subject.transactions.inTransaction {
                subject.users.insert(testUser(userId))
                subject.credentials.save(userId, Credential.GoogleRecord(subjectId))
                Verdict.Commit(Unit)
            }

            subject.transactions.inTransaction {
                Verdict.Commit(subject.credentials.findUserIdByGoogleSubject(subjectId))
            } shouldBe userId
        }

        "a Google subject nobody ever signed in with is not found" {
            val subject = fresh()

            subject.transactions.inTransaction {
                Verdict.Commit(subject.credentials.findUserIdByGoogleSubject(GoogleSubject(Uuid.random().toString())))
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
}
