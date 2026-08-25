package tallyvane.arch

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class MigrationSchemaSpec :
    StringSpec(
        {
            "a migration may not reach a neighbour's tables, which is how a view would do it" {
                val view =
                    """
                create view applications.application_rows as
                select a.id, c.name
                from applications.applications a
                         join jobs.companies c on c.id = a.company_id;
                    """.trimIndent()

                foreignSchemasIn(view, ownSchema = "applications") shouldBe listOf("jobs")
            }

            "a foreign key across schemas is legal and must not be flagged" {
                val table =
                    """
                create table applications.applications
                (
                    id      uuid primary key,
                    user_id uuid not null references identity.users (id) on delete cascade
                );
                    """.trimIndent()

                foreignSchemasIn(table, ownSchema = "applications").shouldBeEmpty()
            }

            "the same file may carry a legal foreign key and an illegal read" {
                val mixed =
                    """
                create table applications.applications
                (
                    user_id uuid not null references identity.users (id)
                );
                select * from jobs.jobs;
                    """.trimIndent()

                foreignSchemasIn(mixed, ownSchema = "applications") shouldBe listOf("jobs")
            }

            "own schema is never foreign, however often it is named" {
                val own = "create index on applications.events (user_id); select * from applications.events;"

                foreignSchemasIn(own, ownSchema = "applications").shouldBeEmpty()
            }

            "a schema named only inside a comment is not a violation" {
                val commented =
                    """
                -- Deliberately not joined to jobs.companies; see ADR-045.
                select * from applications.applications;
                    """.trimIndent()

                foreignSchemasIn(commented, ownSchema = "applications").shouldBeEmpty()
            }

            "Postgres' own namespaces are not neighbours" {
                val extension = "create extension if not exists citext; select * from pg_catalog.pg_tables;"

                foreignSchemasIn(extension, ownSchema = "platform").shouldBeEmpty()
            }

            "every migration in the tree names only its own schema" {
                val offenders =
                    migrationFiles(konsistRoot())
                        .filter { (file, ownSchema) -> foreignSchemasIn(file.readText(), ownSchema).isNotEmpty() }
                        .map { (file, _) -> file.invariantSeparatorsPath }

                offenders.shouldBeEmpty()
            }
        },
    )
