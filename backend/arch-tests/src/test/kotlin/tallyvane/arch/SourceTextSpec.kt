package tallyvane.arch

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class SourceTextSpec :
    StringSpec(
        {
            "removes a KDoc that names a banned call" {
                val source =
                    """
                /**
                 * Why a port rather than kotlin.time.Clock.System.
                 */
                public interface Clock
                    """.trimIndent()

                codeWithoutComments(source) shouldNotContain "kotlin.time.Clock.System"
            }

            "removes a line comment but keeps the code beside it" {
                val source = "val id = next() // UUID.randomUUID() is banned here"

                val code = codeWithoutComments(source)

                code shouldContain "val id = next()"
                code shouldNotContain "UUID.randomUUID"
            }

            "keeps a call that really is in the code" {
                codeWithoutComments("fun now(): Instant = Instant.now()") shouldContain "Instant.now"
            }

            "keeps string literals, which own-schema-only and no-sql-concat read" {
                val code = codeWithoutComments("""val sql = "SELECT * FROM jobs.job" """)

                code shouldContain "SELECT"
                code shouldContain "jobs.job"
            }

            "keeps a raw string whose content looks like a comment" {
                val source = "val pattern = \"\"\"// not a comment /* nor this */\"\"\""

                codeWithoutComments(source) shouldContain "// not a comment"
            }

            "closes a nested block comment once, not twice" {
                val source = "before /* outer /* inner */ still comment */ after"

                val code = codeWithoutComments(source)

                code shouldContain "before"
                code shouldContain "after"
                code shouldNotContain "still comment"
            }

            "leaves a space where a comment interrupted a call" {
                codeWithoutComments("Instant/* sneaky */.now()") shouldNotContain "Instant.now"
            }

            "does not end a string literal on an escaped quote" {
                val source = """val quoted = "a \" UUID.randomUUID() b" """

                codeWithoutComments(source) shouldContain "UUID.randomUUID"
            }

            "runs an unterminated block comment to the end of the file" {
                codeWithoutComments("val a = 1 /* never closed") shouldNotContain "never closed"
            }
        },
    )
