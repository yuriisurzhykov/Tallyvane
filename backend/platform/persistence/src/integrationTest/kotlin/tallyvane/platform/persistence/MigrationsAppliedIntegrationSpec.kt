package tallyvane.platform.persistence

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import tallyvane.platform.observability.health.Ailment
import tallyvane.platform.observability.health.Health
import java.sql.DriverManager

/**
 * `MigrationsApplied` against real Flyway, where the unit spec had a fake.
 *
 * The fake pins the mapping from "pending versions" to `Health`; this pins that Flyway
 * actually reports pending versions for a database nobody migrated, and none for one that was
 * — the half a fake cannot prove. The two databases come from the same fixture, so the
 * difference between the cases is the migration state and nothing else.
 *
 * The last case is the one ADR-051 asks for by name: a probe must verify, not apply. It is
 * asserted over a plain JDBC connection rather than through Flyway, so the observation cannot
 * be satisfied by the thing under test agreeing with itself.
 */
class MigrationsAppliedIntegrationSpec :
    StringSpec(
        {
            "is up on a database the migrations were applied to" {
                val check = MigrationsApplied(FlywayMigrations(PostgresFixture.migrated()))

                check.check() shouldBe Health.Up
            }

            "is down on a database nobody migrated, naming the versions it is behind by" {
                val check = MigrationsApplied(FlywayMigrations(PostgresFixture.empty()))

                val health = check.check()

                val cause = health.shouldBeInstanceOf<Health.Down>().cause
                cause.shouldBeInstanceOf<Ailment.Behind>().versions.shouldNotBeEmpty()
            }

            "leaves the database as it found it, because the deploy applies and readiness verifies" {
                val access = PostgresFixture.empty()

                MigrationsApplied(FlywayMigrations(access)).check()

                schemasIn(access) shouldBe 0
            }
        },
    )

/**
 * How many of our schemas exist, counted over a connection of its own.
 *
 * `platform` is Flyway's own, created by `migrate`; if the check had applied anything, this
 * would be 1 or more.
 */
private fun schemasIn(access: DatabaseAccess): Int =
    DriverManager.getConnection(access.url, access.user, access.password).use { connection ->
        connection.createStatement().use { statement ->
            statement
                .executeQuery(
                    "select count(*) from information_schema.schemata where schema_name = 'platform'",
                ).use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
        }
    }
