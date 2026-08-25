package tallyvane.platform.persistence

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.sql.Connection
import java.sql.DriverManager

private const val PLATFORM_BASELINE = "20260825020000"

class FlywayMigrationsSpec :
    StringSpec(
        {
            val access = PostgresFixture.access()
            val migrations = FlywayMigrations(access)

            fun <T> onFreshConnection(read: (Connection) -> T): T =
                DriverManager.getConnection(access.url, access.user, access.password).use(read)

            fun single(sql: String): String? = onFreshConnection { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(sql).use { rows ->
                        if (rows.next()) rows.getString(1) else null
                    }
                }
            }

            "finds a migration nested under the one location it is given" {
                val applied = migrations.apply()

                // The location is `classpath:db/migration`; the file is one directory
                // deeper, under `platform/`. ADR-051 leans on Flyway walking that, and
                // says so as a claim to be verified where it first runs. This is it.
                applied.count shouldBeGreaterThanOrEqualTo 1
                applied.version shouldBe PLATFORM_BASELINE
            }

            "applies nothing the second time, and still says which version is in place" {
                migrations.apply()

                val second = migrations.apply()

                second.count shouldBe 0
                // Reporting `null` here read as "there is no schema" in a deploy log,
                // when it meant "already up to date".
                second.version shouldBe PLATFORM_BASELINE
            }

            "reports nothing pending once applied" {
                migrations.apply()

                migrations.pending().shouldBeEmpty()
            }

            "keeps its own history table in the platform schema, out of public" {
                migrations.apply()

                single(
                    "select table_schema from information_schema.tables " +
                        "where table_name = 'flyway_schema_history'",
                ) shouldBe "platform"
            }

            "installs citext, and a comparison ignores case on a connection it did not open" {
                migrations.apply()

                onFreshConnection { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute("drop table if exists citext_probe")
                        // Unqualified `citext` only resolves if the migration put the
                        // extension's schema on the database's search_path.
                        statement.execute("create table citext_probe (email citext not null)")
                        statement.execute("insert into citext_probe values ('Ivan@Mail.COM')")
                    }
                }

                // A different case must find the row. If the extension's schema were
                // missing from search_path, this would compare as plain text and return
                // zero - with no error anywhere, which is why it is asserted rather
                // than assumed.
                single("select count(*) from citext_probe where email = 'ivan@mail.com'") shouldBe "1"
            }

            "puts the extension's schema on the database's search_path" {
                migrations.apply()

                single("show search_path") shouldBe "public, platform"
            }

            "loses the type entirely when that schema is taken off search_path" {
                migrations.apply()

                val failure =
                    onFreshConnection { connection ->
                        connection.createStatement().use { statement ->
                            statement.execute("set search_path to public")
                            runCatching {
                                statement.execute("create temporary table probe (email citext)")
                            }.exceptionOrNull()
                        }
                    }

                // Proves the migration's third statement is load-bearing rather than
                // decoration: with `platform` off search_path, not even the type
                // resolves. The nastier variant - a qualified column type whose
                // operators are invisible, comparing case-sensitively with no error -
                // is what that statement exists to make unreachable.
                failure?.message shouldContain "citext"
            }
        },
    )
