package tallyvane.identity.application.port

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.session.Session
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.HashedToken
import tallyvane.identity.domain.token.TokenFamilyId
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The behaviour every [SessionStore] must show, whatever stores it — the fake here, and
 * `SessionStoreOverExposed` against a real Postgres in `identity:infrastructure` (ADR-046).
 * [Subject] carries a [UserRepository] for the same reason [CredentialRepositoryConformance]
 * does: a real row has a foreign key to `identity.users`.
 */
public abstract class SessionStoreConformance : StringSpec() {
    protected abstract suspend fun fresh(): Subject

    public interface Subject {
        public val users: UserRepository
        public val sessions: SessionStore
        public val transactions: TransactionRunner
    }

    init {
        "a saved session is found back by its own id, unchanged" {
            val subject = fresh()
            val userId = UserId(Uuid.random())
            val session = testSession(userId)

            subject.transactions.inTransaction {
                subject.users.insert(testUser(userId))
                subject.sessions.save(session)
                Verdict.Commit(Unit)
            }

            subject.transactions.inTransaction {
                Verdict.Commit(subject.sessions.find(session.id))
            } shouldBe session
        }

        "an id nobody ever saved a session under is not found" {
            val subject = fresh()

            subject.transactions.inTransaction {
                Verdict.Commit(subject.sessions.find(SessionId(Uuid.random())))
            }.shouldBeNull()
        }

        "revoke marks the session revoked at the given instant, without deleting it" {
            val subject = fresh()
            val userId = UserId(Uuid.random())
            val session = testSession(userId)
            val revokedAt = Instant.parse("2026-03-01T00:00:00Z")

            subject.transactions.inTransaction {
                subject.users.insert(testUser(userId))
                subject.sessions.save(session)
                Verdict.Commit(Unit)
            }
            subject.transactions.inTransaction {
                subject.sessions.revoke(session.id, revokedAt)
                Verdict.Commit(Unit)
            }

            subject.transactions.inTransaction {
                Verdict.Commit(subject.sessions.find(session.id))
            } shouldBe session.copy(revokedAt = revokedAt)
        }

        "revokeAllFor revokes every session for that user, and no other user's" {
            val subject = fresh()
            val userId = UserId(Uuid.random())
            val otherUserId = UserId(Uuid.random())
            val first = testSession(userId)
            val second = testSession(userId)
            val untouched = testSession(otherUserId)
            val revokedAt = Instant.parse("2026-03-01T00:00:00Z")

            subject.transactions.inTransaction {
                subject.users.insert(testUser(userId))
                subject.users.insert(testUser(otherUserId))
                subject.sessions.save(first)
                subject.sessions.save(second)
                subject.sessions.save(untouched)
                Verdict.Commit(Unit)
            }
            subject.transactions.inTransaction {
                subject.sessions.revokeAllFor(userId, revokedAt)
                Verdict.Commit(Unit)
            }

            subject.transactions.inTransaction {
                Verdict.Commit(subject.sessions.listFor(userId))
            } shouldContainExactlyInAnyOrder
                listOf(first.copy(revokedAt = revokedAt), second.copy(revokedAt = revokedAt))
            subject.transactions.inTransaction {
                Verdict.Commit(subject.sessions.find(untouched.id))
            } shouldBe untouched
        }

        "an attached access token's hash finds the session back, before it expires" {
            val subject = fresh()
            val userId = UserId(Uuid.random())
            val session = testSession(userId)
            val hash = testHash()
            val expiresAt = Instant.parse("2026-01-01T01:00:00Z")
            val attachedAt = Instant.parse("2026-01-01T00:30:00Z")

            subject.transactions.inTransaction {
                subject.users.insert(testUser(userId))
                subject.sessions.save(session)
                subject.sessions.attachAccessToken(session.id, hash, expiresAt, lastUsedAt = attachedAt)
                Verdict.Commit(Unit)
            }

            subject.transactions.inTransaction {
                Verdict.Commit(subject.sessions.findByAccessTokenHash(hash, now = expiresAt - 1.minutes))
            } shouldBe session.copy(lastUsedAt = attachedAt)
        }

        "attaching an access token stamps lastUsedAt with the instant given, not left at createdAt" {
            val subject = fresh()
            val userId = UserId(Uuid.random())
            val session = testSession(userId)
            val attachedAt = Instant.parse("2026-02-01T00:00:00Z")

            subject.transactions.inTransaction {
                subject.users.insert(testUser(userId))
                subject.sessions.save(session)
                subject.sessions.attachAccessToken(
                    session.id,
                    testHash(),
                    expiresAt = attachedAt + 15.minutes,
                    lastUsedAt = attachedAt,
                )
                Verdict.Commit(Unit)
            }

            subject.transactions.inTransaction {
                Verdict.Commit(subject.sessions.find(session.id))
            } shouldBe session.copy(lastUsedAt = attachedAt)
        }

        "an access token hash is not found once its own expiry has passed" {
            val subject = fresh()
            val userId = UserId(Uuid.random())
            val session = testSession(userId)
            val hash = testHash()
            val expiresAt = Instant.parse("2026-01-01T01:00:00Z")

            subject.transactions.inTransaction {
                subject.users.insert(testUser(userId))
                subject.sessions.save(session)
                subject.sessions.attachAccessToken(session.id, hash, expiresAt, lastUsedAt = session.createdAt)
                Verdict.Commit(Unit)
            }

            subject.transactions.inTransaction {
                Verdict.Commit(subject.sessions.findByAccessTokenHash(hash, now = expiresAt + 1.minutes))
            }.shouldBeNull()
        }

        "an access token hash is not found once its session is revoked" {
            val subject = fresh()
            val userId = UserId(Uuid.random())
            val session = testSession(userId)
            val hash = testHash()
            val expiresAt = Instant.parse("2026-01-01T01:00:00Z")

            subject.transactions.inTransaction {
                subject.users.insert(testUser(userId))
                subject.sessions.save(session)
                subject.sessions.attachAccessToken(session.id, hash, expiresAt, lastUsedAt = session.createdAt)
                Verdict.Commit(Unit)
            }
            subject.transactions.inTransaction {
                subject.sessions.revoke(session.id, Instant.parse("2026-01-01T00:30:00Z"))
                Verdict.Commit(Unit)
            }

            subject.transactions.inTransaction {
                Verdict.Commit(subject.sessions.findByAccessTokenHash(hash, now = expiresAt - 1.minutes))
            }.shouldBeNull()
        }

        "a hash nobody ever attached is not found" {
            val subject = fresh()

            subject.transactions.inTransaction {
                Verdict.Commit(
                    subject.sessions.findByAccessTokenHash(testHash(), now = Instant.parse("2026-01-01T00:00:00Z")),
                )
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

    private fun testSession(userId: UserId): Session = Session(
        id = SessionId(Uuid.random()),
        userId = userId,
        device = DeviceLabel("Chrome on MacBook"),
        tokenFamilyId = TokenFamilyId(Uuid.random()),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        lastUsedAt = Instant.parse("2026-01-01T00:00:00Z"),
        revokedAt = null,
    )

    private fun testHash(): HashedToken = HashedToken(Secret(Uuid.random().toString()), pepperVersion = 1)
}
