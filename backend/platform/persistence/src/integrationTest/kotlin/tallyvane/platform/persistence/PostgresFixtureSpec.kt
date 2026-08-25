package tallyvane.platform.persistence

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.sql.DriverManager

class PostgresFixtureSpec :
    StringSpec(
        {
            "reaches a database that answers" {
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

            "runs the major version the compose file runs" {
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

            "hands out the same database on a second call, rather than a second container" {
                PostgresFixture.access() shouldBe PostgresFixture.access()
            }

            "says how to reach it without saying what started it" {
                PostgresFixture.access().url shouldContain "jdbc:postgresql://"
            }
        },
    )
