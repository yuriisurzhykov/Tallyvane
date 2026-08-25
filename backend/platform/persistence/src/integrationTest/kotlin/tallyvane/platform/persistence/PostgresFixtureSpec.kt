package tallyvane.platform.persistence

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import java.sql.DriverManager

/**
 * Two tests, and neither is coverage of Tallyvane.
 *
 * The first is a canary for this environment: Docker, the image, the driver and
 * the fixture's wiring. It cannot fail because of a bug in our code, and must not
 * be counted as if it could. The second pins test-against-production parity, which
 * is the one claim here that a future change can quietly break.
 *
 * An earlier draft had two more. One asserted that the returned url contains
 * `jdbc:postgresql://` — a string Testcontainers produces, unfailable by anything
 * we write. The other asserted that a second call hands back the same database,
 * which contradicted the isolation already agreed for slice 8: a database per spec,
 * cloned from a migrated template. A test that has to be deleted for the plan to
 * proceed reads like a decision, and is worse than no test at all.
 */
class PostgresFixtureSpec :
    StringSpec(
        {
            "reaches a database that answers, which is this environment's canary" {
                val access = PostgresFixture.access()

                DriverManager.getConnection(access.url, access.user, access.password).use { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("select 1").use { rows ->
                            rows.next() shouldBe true
                            rows.getInt(1) shouldBe 1
                        }
                    }
                }
            }

            "runs the major version production runs, so a test cannot pass on a different Postgres" {
                val access = PostgresFixture.access()

                DriverManager.getConnection(access.url, access.user, access.password).use { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("show server_version").use { rows ->
                            rows.next() shouldBe true
                            rows.getString(1) shouldStartWith "17"
                        }
                    }
                }
            }
        },
    )
