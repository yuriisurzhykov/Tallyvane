package tallyvane.platform.persistence

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import java.sql.Connection
import java.sql.DriverManager

private fun <T> on(access: DatabaseAccess, read: (Connection) -> T): T =
    DriverManager.getConnection(access.url, access.user, access.password).use(read)

private fun query(access: DatabaseAccess, sql: String): String? = on(access) { connection ->
    connection.createStatement().use { statement ->
        statement.executeQuery(sql).use { rows -> if (rows.next()) rows.getString(1) else null }
    }
}

/**
 * Two of these are canaries for this environment - Docker, the image, the driver, the
 * wiring - and cannot fail because of a bug in our code. The isolation cases are not:
 * every other integration spec depends on the claim that its database is its own, so that
 * claim is asserted rather than trusted.
 */
class PostgresFixtureSpec :
    StringSpec(
        {
            "reaches a database that answers, which is this environment's canary" {
                query(PostgresFixture.empty(), "select 1") shouldBe "1"
            }

            // A constant compared against a constant: this passes on whatever
            // PostgresFixture starts, and would go on passing after the compose file moved
            // to 18. Named as the weak check it is; the debt is recorded in backend/.plans/.
            "runs the major version production runs, so a test cannot pass on a different Postgres" {
                query(PostgresFixture.empty(), "show server_version")!! shouldStartWith "17"
            }

            "hands out a different database every time" {
                PostgresFixture.empty().url shouldNotBe PostgresFixture.empty().url
            }

            "keeps one caller's tables invisible to the next" {
                val mine = PostgresFixture.empty()
                on(mine) { connection ->
                    connection.createStatement().use { it.execute("create table only_mine (n integer)") }
                }

                val theirs = PostgresFixture.empty()

                query(
                    theirs,
                    "select count(*) from information_schema.tables where table_name = 'only_mine'",
                ) shouldBe "0"
            }

            "hands out a migrated database with the platform schema already in place" {
                val access = PostgresFixture.migrated()

                query(
                    access,
                    "select count(*) from information_schema.schemata where schema_name = 'platform'",
                ) shouldBe "1"
            }

            "hands out an empty database with no schema history at all" {
                val access = PostgresFixture.empty()

                query(
                    access,
                    "select count(*) from information_schema.tables where table_name = 'flyway_schema_history'",
                ) shouldBe "0"
            }
        },
    )
