package tallyvane.server

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.http.HttpStatusCode
import tallyvane.platform.persistence.DatabaseAccess
import tallyvane.platform.persistence.PostgresFixture
import java.sql.DriverManager
import kotlin.time.Duration.Companion.milliseconds

// Ktor's own vocabulary rather than two numbers of ours: the server names them the same way.
private val OK = HttpStatusCode.OK.value

private val UNAVAILABLE = HttpStatusCode.ServiceUnavailable.value

/**
 * The process against a real database, over a real socket.
 *
 * `ApplicationSpec` covers the other half — what happens with no database at all — and needs no
 * Docker for it, because there is nothing to start.
 */
class ApplicationIntegrationSpec :
    StringSpec(
        {
            // C3
            "is ready against a database the deploy migrated" {
                val settings = settings(PostgresFixture.migrated())

                Application(settings).use { application ->
                    application.start()

                    awaited(OK) { get(settings.port, "/api/v1/health/ready").statusCode() } shouldBe OK
                }
            }

            // C4. C3 alone would pass on a wiring that registered no checks at all: an aggregate
            // over zero checks is trivially up. This is the case that says the real checks are in
            // the list.
            "reports the real checks by name to a reader holding the token" {
                val settings = settings(PostgresFixture.migrated())

                Application(settings).use { application ->
                    application.start()

                    val body = get(settings.port, "/api/v1/health", token = TOKEN).body()

                    body shouldContain "database"
                    body shouldContain "schema"
                }
            }

            "tells a reader without the token nothing but the status" {
                val settings = settings(PostgresFixture.migrated())

                Application(settings).use { application ->
                    application.start()

                    val body = get(settings.port, "/api/v1/health").body()

                    body shouldNotContain "database"
                    body shouldNotContain "schema"
                }
            }

            // C5. ADR-051's ordering, pinned from the outside: the deploy applies migrations and
            // the application verifies them. Fails the moment someone makes startup "helpfully"
            // migrate, which would also make readiness a report on work it had just done itself.
            "does not migrate on startup, and says it is not ready because of it" {
                val access = PostgresFixture.empty()
                val settings = settings(access)

                Application(settings).use { application ->
                    application.start()

                    val status = awaited(UNAVAILABLE) {
                        get(settings.port, "/api/v1/health/ready").statusCode()
                    }

                    status shouldBe UNAVAILABLE
                    schemasIn(access) shouldBe 0
                }
            }

            // C6. The case decision B exists for. Without it, "comes up and reports not ready" is
            // an assertion about a state nobody has seen the process leave.
            "goes not-ready when the database stops answering, and ready again without a restart" {
                val settings = settings(PostgresFixture.migrated())

                Application(settings).use { application ->
                    application.start()

                    awaited(OK) { get(settings.port, "/api/v1/health/ready").statusCode() } shouldBe OK

                    val frozen = PostgresFixture.frozen {
                        awaited(UNAVAILABLE) {
                            get(settings.port, "/api/v1/health/ready").statusCode()
                        }
                    }

                    frozen shouldBe UNAVAILABLE
                    awaited(OK) { get(settings.port, "/api/v1/health/ready").statusCode() } shouldBe OK
                }
            }

            // ADR-086. The sweep itself is proven in `ClaimsSweepSpec`; this is the case that the process
            // actually starts one, which no unit case can see.
            "forgets idempotency claims whose day is over while it runs" {
                val access = PostgresFixture.migrated()
                leaveClaimOfYesterday(access)

                Application(settings(access), claimsSweepEvery = SWEEP_EVERY).use { application ->
                    application.start()

                    awaited(0) { claimsIn(access) } shouldBe 0
                }
            }
        },
    )

private val SWEEP_EVERY = 50.milliseconds

/**
 * A claim whose day ended a day ago, put there over a connection of its own.
 */
private fun leaveClaimOfYesterday(access: DatabaseAccess) {
    DriverManager.getConnection(access.url, access.user, access.password.revealed()).use { connection ->
        connection.createStatement().use { statement ->
            statement.execute(
                "insert into platform.idempotency_keys (owner, key, fingerprint, created_at, expires_at) " +
                    "values ('anonymous', gen_random_uuid(), decode(repeat('00', 32), 'hex'), " +
                    "now() - interval '2 days', now() - interval '1 day')",
            )
        }
    }
}

private fun claimsIn(access: DatabaseAccess): Int = DriverManager
    .getConnection(access.url, access.user, access.password.revealed())
    .use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("select count(*) from platform.idempotency_keys").use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }
    }

/**
 * Whether Flyway's own schema exists, counted over a connection of its own — the observation cannot
 * be satisfied by the application agreeing with itself.
 */
private fun schemasIn(access: DatabaseAccess): Int = DriverManager
    .getConnection(access.url, access.user, access.password.revealed())
    .use { connection ->
        connection
            .createStatement()
            .use { statement ->
                statement
                    .executeQuery(
                        "select count(*) from information_schema.schemata where schema_name = 'platform'",
                    ).use { rows ->
                        rows.next()
                        rows.getInt(1)
                    }
            }
    }
