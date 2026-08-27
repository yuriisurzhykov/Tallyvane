package tallyvane.app

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import tallyvane.platform.persistence.DatabaseAccess
import tallyvane.platform.persistence.PostgresFixture
import java.sql.DriverManager

private const val OK = 200

private const val UNAVAILABLE = 503

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
        },
    )

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
