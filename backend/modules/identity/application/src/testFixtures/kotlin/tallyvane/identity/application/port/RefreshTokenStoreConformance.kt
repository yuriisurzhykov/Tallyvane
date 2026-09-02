package tallyvane.identity.application.port

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.session.Session
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.HashedToken
import tallyvane.identity.domain.token.TokenFamilyId
import tallyvane.identity.domain.token.TokenFamilyState
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The behaviour every [RefreshTokenStore] must show, whatever stores it — the fake here, and
 * `RefreshTokenStoreOverExposed` against a real Postgres in `identity:infrastructure`
 * (ADR-046). [Subject] carries both a [UserRepository] and a [SessionStore]: a real row has a
 * foreign key to `identity.sessions`, which itself has one to `identity.users`.
 */
public abstract class RefreshTokenStoreConformance : StringSpec() {
    protected abstract suspend fun fresh(): Subject

    public interface Subject {
        public val users: UserRepository
        public val sessions: SessionStore
        public val refreshTokens: RefreshTokenStore
        public val transactions: TransactionRunner
    }

    init {
        "a token this store minted is not yet used, under the session it was minted for" {
            val subject = fresh()
            val session = sessionFor(subject)
            val hash = testHash()

            subject.transactions.inTransaction {
                subject.refreshTokens.issueFirst(session.id, session.tokenFamilyId, hash, expiresAt(), now())
                Verdict.Commit(Unit)
            }

            subject.transactions.inTransaction {
                Verdict.Commit(subject.refreshTokens.stateOf(hash))
            } shouldBe TokenFamilyState(session.id, used = false)
        }

        "a hash this store never minted has no state at all" {
            val subject = fresh()

            subject.transactions.inTransaction {
                Verdict.Commit(subject.refreshTokens.stateOf(testHash()))
            }.shouldBeNull()
        }

        "rotating an active token consumes it and mints a new one, active, same family" {
            val subject = fresh()
            val session = sessionFor(subject)
            val oldHash = testHash()
            val newHash = testHash()

            subject.transactions.inTransaction {
                subject.refreshTokens.issueFirst(session.id, session.tokenFamilyId, oldHash, expiresAt(), now())
                Verdict.Commit(Unit)
            }

            val outcome = subject.transactions.inTransaction {
                Verdict.Commit(subject.refreshTokens.rotate(oldHash, newHash, expiresAt(), now()))
            }
            outcome shouldBe RefreshTokenStore.RotateOutcome.Rotated(session.id)

            subject.transactions.inTransaction {
                Verdict.Commit(subject.refreshTokens.stateOf(oldHash))
            } shouldBe TokenFamilyState(session.id, used = true)
            subject.transactions.inTransaction {
                Verdict.Commit(subject.refreshTokens.stateOf(newHash))
            } shouldBe TokenFamilyState(session.id, used = false)
        }

        "rotating an already-consumed token reports ALREADY_ROTATED and mints nothing" {
            val subject = fresh()
            val session = sessionFor(subject)
            val oldHash = testHash()
            val firstNewHash = testHash()
            val secondNewHash = testHash()

            subject.transactions.inTransaction {
                subject.refreshTokens.issueFirst(session.id, session.tokenFamilyId, oldHash, expiresAt(), now())
                Verdict.Commit(Unit)
            }
            subject.transactions.inTransaction {
                subject.refreshTokens.rotate(oldHash, firstNewHash, expiresAt(), now())
                Verdict.Commit(Unit)
            }

            val outcome = subject.transactions.inTransaction {
                Verdict.Commit(subject.refreshTokens.rotate(oldHash, secondNewHash, expiresAt(), now()))
            }
            outcome shouldBe RefreshTokenStore.RotateOutcome.AlreadyRotated
            subject.transactions.inTransaction {
                Verdict.Commit(subject.refreshTokens.stateOf(secondNewHash))
            }.shouldBeNull()
        }

        "rotating a hash this store never minted reports ALREADY_ROTATED" {
            val subject = fresh()

            subject.transactions.inTransaction {
                Verdict.Commit(subject.refreshTokens.rotate(testHash(), testHash(), expiresAt(), now()))
            } shouldBe RefreshTokenStore.RotateOutcome.AlreadyRotated
        }

        "revokeAllFor marks a still-active token used, so it can no longer be rotated" {
            val subject = fresh()
            val session = sessionFor(subject)
            val hash = testHash()

            subject.transactions.inTransaction {
                subject.refreshTokens.issueFirst(session.id, session.tokenFamilyId, hash, expiresAt(), now())
                Verdict.Commit(Unit)
            }
            subject.transactions.inTransaction {
                subject.refreshTokens.revokeAllFor(session.id)
                Verdict.Commit(Unit)
            }

            subject.transactions.inTransaction {
                Verdict.Commit(subject.refreshTokens.stateOf(hash))
            } shouldBe TokenFamilyState(session.id, used = true)
        }

        "deleteIssuedBefore removes a token older than the cutoff, and reports how many" {
            val subject = fresh()
            val session = sessionFor(subject)
            val hash = testHash()

            subject.transactions.inTransaction {
                subject.refreshTokens.issueFirst(session.id, session.tokenFamilyId, hash, expiresAt(), now())
                Verdict.Commit(Unit)
            }

            val deleted = subject.transactions.inTransaction {
                Verdict.Commit(subject.refreshTokens.deleteIssuedBefore(cutoff = now() + 1.days))
            }

            deleted shouldBe 1
            subject.transactions.inTransaction {
                Verdict.Commit(subject.refreshTokens.stateOf(hash))
            }.shouldBeNull()
        }

        "deleteIssuedBefore leaves a token issued at or after the cutoff untouched" {
            val subject = fresh()
            val session = sessionFor(subject)
            val hash = testHash()

            subject.transactions.inTransaction {
                subject.refreshTokens.issueFirst(session.id, session.tokenFamilyId, hash, expiresAt(), now())
                Verdict.Commit(Unit)
            }

            val deleted = subject.transactions.inTransaction {
                Verdict.Commit(subject.refreshTokens.deleteIssuedBefore(cutoff = now() - 1.days))
            }

            deleted shouldBe 0
            subject.transactions.inTransaction {
                Verdict.Commit(subject.refreshTokens.stateOf(hash))
            } shouldBe TokenFamilyState(session.id, used = false)
        }
    }

    private suspend fun sessionFor(subject: Subject): Session {
        val userId = UserId(Uuid.random())
        val session = testSession(userId)
        subject.transactions.inTransaction {
            subject.users.insert(testUser(userId))
            subject.sessions.save(session)
            Verdict.Commit(Unit)
        }
        return session
    }

    private fun now(): Instant = Instant.parse("2026-01-01T00:00:00Z")

    private fun expiresAt(): Instant = now() + 30.days

    private fun testUser(id: UserId): User = User(
        id = id,
        email = Email("person-${Uuid.random()}@example.com"),
        displayName = null,
        createdAt = now(),
        disabledAt = null,
    )

    private fun testSession(userId: UserId): Session = Session(
        id = SessionId(Uuid.random()),
        userId = userId,
        device = DeviceLabel("Chrome on MacBook"),
        tokenFamilyId = TokenFamilyId(Uuid.random()),
        createdAt = now(),
        lastUsedAt = now(),
        revokedAt = null,
    )

    private fun testHash(): HashedToken = HashedToken(Secret(Uuid.random().toString()), pepperVersion = 1)
}
