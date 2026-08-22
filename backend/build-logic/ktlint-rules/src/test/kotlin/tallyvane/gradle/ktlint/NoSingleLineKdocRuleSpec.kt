package tallyvane.gradle.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import io.kotest.core.spec.style.StringSpec

class NoSingleLineKdocRuleSpec :
    StringSpec({
        val rule = assertThatRule { NoSingleLineKdocRule() }

        "wraps a one-line KDoc on a function" {
            rule(
                """
                |/** The current instant according to this clock. */
                |fun now() = Unit
                """.trimMargin(),
            ).hasLintViolation(1, 1, "A KDoc comment must use the multiline form")
                .isFormattedAs(
                    """
                    |/**
                    | * The current instant according to this clock.
                    | */
                    |fun now() = Unit
                    """.trimMargin(),
                )
        }

        "wraps a one-line KDoc on a constructor property" {
            rule(
                """
                |class ArchitectureException(
                |    /** Konsist rule code being broken. */
                |    val rule: String,
                |)
                """.trimMargin(),
            ).isFormattedAs(
                """
                |class ArchitectureException(
                |    /**
                |     * Konsist rule code being broken.
                |     */
                |    val rule: String,
                |)
                """.trimMargin(),
            )
        }

        "leaves an already multiline KDoc alone" {
            rule(
                """
                |/**
                | * Time as a collaborator, not as a static call.
                | */
                |interface Clock
                """.trimMargin(),
            ).hasNoLintViolations()
        }

        "does not treat a block comment as KDoc" {
            rule(
                """
                |/* not kdoc */
                |fun now() = Unit
                """.trimMargin(),
            ).hasNoLintViolations()
        }

        "wraps an empty one-line KDoc" {
            rule(
                """
                |/** */
                |fun now() = Unit
                """.trimMargin(),
            ).isFormattedAs(
                """
                |/**
                | *
                | */
                |fun now() = Unit
                """.trimMargin(),
            )
        }
    })
