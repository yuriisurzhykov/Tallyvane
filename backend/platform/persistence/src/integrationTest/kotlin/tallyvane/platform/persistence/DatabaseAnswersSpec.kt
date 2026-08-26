package tallyvane.platform.persistence

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import tallyvane.platform.observability.health.Ailment
import tallyvane.platform.observability.health.Health
import tallyvane.platform.observability.health.HealthCheck

/**
 * `DatabaseAnswers` against a real Postgres, and against one it cannot reach.
 *
 * The second half is what makes the first mean anything. Verified rather than argued: with
 * `check()` replaced by a bare `Health.Up`, the first case still passed and both closed-pool
 * cases failed. So the up-case is a canary and the closed-pool cases are the coverage — they
 * assert that the check throws rather than reporting `up`, and that once wrapped as production
 * wraps it the answer is `Down` carrying an exception *type* and no driver message. §17 forbids
 * hosts and ports reaching a client, and a health body is a client-facing place.
 *
 * What no case here catches: a check that opened a transaction and never issued its statement
 * would pass all four, because the failure it exists for — a server that accepts connections
 * but cannot answer — is not reproducible from outside without breaking the server itself.
 * Stated rather than papered over.
 */
class DatabaseAnswersSpec :
    StringSpec(
        {
            "is up against a database it can reach through the pool" {
                PostgresPersistence(PostgresFixture.empty()).use { persistence ->
                    DatabaseAnswers(persistence.transactions).check() shouldBe Health.Up
                }
            }

            "throws rather than reporting up when the pool is gone" {
                val persistence = PostgresPersistence(PostgresFixture.empty())
                val check = DatabaseAnswers(persistence.transactions)
                persistence.close()

                shouldThrowAny { check.check() }
            }

            "wrapped as production wraps it, an unreachable database is down and leaks no detail" {
                val persistence = PostgresPersistence(PostgresFixture.empty())
                val check = HealthCheck.Contained(DatabaseAnswers(persistence.transactions))
                persistence.close()

                val health = check.check()

                val cause = health.shouldBeInstanceOf<Health.Down>().cause
                val threw = cause.shouldBeInstanceOf<Ailment.Threw>()
                threw.type shouldNotContain "jdbc"
                threw.type shouldNotContain "postgresql"
            }

            "is required for readiness, and says so through the wrapper too" {
                PostgresPersistence(PostgresFixture.empty()).use { persistence ->
                    val check = HealthCheck.Contained(DatabaseAnswers(persistence.transactions))

                    check.name shouldBe "database"
                    check.requiredForReadiness shouldBe true
                }
            }
        },
    )
