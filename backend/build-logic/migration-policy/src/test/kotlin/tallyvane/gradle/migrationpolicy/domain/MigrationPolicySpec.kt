package tallyvane.gradle.migrationpolicy.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class MigrationPolicySpec :
    StringSpec({
        "an additive migration is clean" {
            MigrationPolicy()
                .findings(
                    listOf(
                        migration(
                            """
                            alter table jobs.companies add column legacy_name text;
                            create index concurrently_wont_be_used on jobs.companies (legacy_name);
                            """,
                        ),
                    ),
                ).shouldBeEmpty()
        }

        "DROP TABLE is a finding" {
            findingLabels("drop table jobs.companies;") shouldBe listOf("DROP TABLE")
        }

        "DROP COLUMN is a finding" {
            findingLabels("alter table jobs.companies drop column legacy_name;") shouldBe listOf("DROP COLUMN")
        }

        "SET NOT NULL is a finding" {
            findingLabels("alter table jobs.companies alter column name set not null;") shouldBe listOf("SET NOT NULL")
        }

        "RENAME COLUMN is a finding" {
            findingLabels("alter table jobs.companies rename column name to full_name;") shouldBe
                listOf("RENAME COLUMN")
        }

        "RENAME TO is a finding" {
            findingLabels("alter table jobs.companies rename to firms;") shouldBe listOf("RENAME TO")
        }

        // The exact case ADR-066 calls out: a migration documenting why a later, separate
        // release will drop something must not trip on its own explanation of one.
        "a forbidden statement inside a comment is not a finding" {
            findingLabels(
                """
                -- a future contract release will: drop column legacy_name
                create table jobs.companies (id uuid primary key);
                """,
            ).shouldBeEmpty()
        }

        "a forbidden statement inside a multi-line block comment is not a finding" {
            findingLabels(
                """
                /* deferred to a contract release:
                   drop column legacy_name
                */
                create table jobs.companies (id uuid primary key);
                """,
            ).shouldBeEmpty()
        }

        // The line number in the message must survive a block comment spanning several
        // lines — this is the one property withoutSqlComments exists to preserve.
        "the reported line number accounts for a multi-line block comment above it" {
            val sql =
                """
                /* line 1
                   line 2
                   line 3 */
                drop table jobs.companies;
                """.trimIndent()

            val findings = MigrationPolicy().findings(listOf(MigrationFile("V1__x.sql", sql)))

            findings shouldBe
                listOf(
                    Finding(
                        "V1__x.sql:4: DROP TABLE is not additive — split into an expand release and a later " +
                            "contract release (ADR-066)",
                    ),
                )
        }

        "one finding per file, not merged across files" {
            val findings =
                MigrationPolicy().findings(
                    listOf(
                        MigrationFile("V1__a.sql", "drop table a;"),
                        MigrationFile("V2__b.sql", "drop table b;"),
                    ),
                )

            findings.map(Finding::toString).size shouldBe 2
        }
    })

private fun migration(sql: String): MigrationFile = MigrationFile("V1__test.sql", sql.trimIndent())

private fun findingLabels(sql: String): List<String> =
    MigrationPolicy()
        .findings(listOf(migration(sql)))
        .map { finding -> finding.toString().substringAfter(": ").substringBefore(" is not additive") }
