package tallyvane.gradle.migrationpolicy.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class MigrationTextSpec :
    StringSpec({
        "a line comment is blanked, the rest of the line is not" {
            withoutSqlComments("select 1; -- a trailing note") shouldBe "select 1; "
        }

        "a block comment on one line is blanked" {
            withoutSqlComments("select /* inline */ 1;") shouldBe "select  1;"
        }

        // The property this function exists for: a block comment spanning several lines must
        // not shift the line number of anything after it.
        "a multi-line block comment is replaced by the newlines it contained" {
            val sql =
                """
                /* one
                two
                three */
                select 1;
                """.trimIndent()

            withoutSqlComments(sql).lines().size shouldBe sql.lines().size
        }

        "content is unrelated to comments is untouched" {
            withoutSqlComments("create table x (id uuid primary key);") shouldBe
                "create table x (id uuid primary key);"
        }
    })
