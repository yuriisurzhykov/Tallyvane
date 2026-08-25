package tallyvane.arch

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import java.io.File

class ExceptionBudgetSpec :
    StringSpec(
        {
            "architecture exceptions are justified" {
                val broken =
                    productionScope().recordedExceptions().filter { exception ->
                        exception.reason.length < MIN_EXCEPTION_REASON ||
                            !ADR_PATTERN.matches(exception.adr) ||
                            !adrFileExists(exception.adr) ||
                            exception.rule !in KNOWN_RULES
                    }
                broken.shouldBeEmpty()
            }

            "architecture exceptions stay within budget" {
                productionScope().recordedExceptions().size.shouldBeLessThanOrEqual(MAX_EXCEPTIONS)
            }

            "every known rule has a fixture directory" {
                val missing =
                    KNOWN_RULES.filter { rule ->
                        !File(konsistRoot(), "arch-tests/src/test/resources/konsist-fixtures/$rule").isDirectory
                    }
                missing.shouldBeEmpty()
            }

            "known rules match the catalog" {
                KNOWN_RULES.shouldBe(ARCH_RULES.map { it.id }.toSet())
            }
        },
    )
