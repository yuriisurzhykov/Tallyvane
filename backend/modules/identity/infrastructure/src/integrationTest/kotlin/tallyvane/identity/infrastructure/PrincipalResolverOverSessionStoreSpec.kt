package tallyvane.identity.infrastructure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import tallyvane.identity.application.port.TokenFactory
import tallyvane.identity.application.port.TokenHasher
import tallyvane.identity.contract.Principal
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.session.Session
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.TokenFamilyId
import tallyvane.identity.domain.token.TokenKind
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.identity.infrastructure.persistence.SessionStoreOverExposed
import tallyvane.identity.infrastructure.persistence.UserRepositoryOverExposed
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.Verdict
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * [PrincipalResolverOverSessionStore] against a real Postgres — the one adapter that has no
 * conformance suite of its own (only one implementation exists, per ADR-046 and the design's own
 * note), so its correctness rests entirely on this integration spec.
 */
class PrincipalResolverOverSessionStoreSpec :
    StringSpec({
        val hasher = TokenHasher.Hmac(Secret("pepper"), pepperVersion = 1)
        val factory = TokenFactory.Csprng()
        val now = Instant.parse("2026-01-01T00:00:00Z")

        fun resolver(persistence: PostgresPersistence, clockAt: Instant = now) = PrincipalResolverOverSessionStore(
            sessions = SessionStoreOverExposed(),
            tokenHasher = hasher,
            clock = ClockFake(clockAt),
            transactions = persistence.transactions,
        )

        "an access token attached to a real session resolves to that session's own user" {
            PostgresPersistence(PostgresFixture.migrated()).use { persistence ->
                val userId = UserId(Uuid.random())
                val sessionId = SessionId(Uuid.random())
                val access = factory.mint(TokenKind.ACCESS)
                val users = UserRepositoryOverExposed()
                val sessions = SessionStoreOverExposed()

                persistence.transactions.inTransaction {
                    users.insert(
                        User(userId, Email("person-${Uuid.random()}@example.com"), null, now, null),
                    )
                    sessions.save(
                        Session(sessionId, userId, DeviceLabel("Chrome"), TokenFamilyId(Uuid.random()), now, now, null),
                    )
                    sessions.attachAccessToken(sessionId, hasher.hash(access), now + 15.minutes, now)
                    Verdict.Commit(Unit)
                }

                val resolved = resolver(persistence).resolve(access.raw)

                resolved.shouldNotBeNull()
                val principal = resolved.principal.shouldBeInstanceOf<Principal.User>()
                principal.id.value shouldBe userId.value
                resolved.sessionId.value shouldBe sessionId.value
            }
        }

        "an unknown access token resolves to nothing" {
            PostgresPersistence(PostgresFixture.migrated()).use { persistence ->
                resolver(persistence).resolve(factory.mint(TokenKind.ACCESS).raw).shouldBeNull()
            }
        }

        "a malformed cookie value resolves to nothing rather than throwing" {
            PostgresPersistence(PostgresFixture.migrated()).use { persistence ->
                resolver(persistence).resolve("not-a-real-token-value-at-all").shouldBeNull()
            }
        }

        "an expired access token resolves to nothing" {
            PostgresPersistence(PostgresFixture.migrated()).use { persistence ->
                val userId = UserId(Uuid.random())
                val sessionId = SessionId(Uuid.random())
                val access = factory.mint(TokenKind.ACCESS)
                val users = UserRepositoryOverExposed()
                val sessions = SessionStoreOverExposed()

                persistence.transactions.inTransaction {
                    users.insert(User(userId, Email("person-${Uuid.random()}@example.com"), null, now, null))
                    sessions.save(
                        Session(sessionId, userId, DeviceLabel("Chrome"), TokenFamilyId(Uuid.random()), now, now, null),
                    )
                    sessions.attachAccessToken(sessionId, hasher.hash(access), now + 15.minutes, now)
                    Verdict.Commit(Unit)
                }

                resolver(persistence, clockAt = now + 30.minutes).resolve(access.raw).shouldBeNull()
            }
        }
    })
