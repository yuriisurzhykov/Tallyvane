package tallyvane.platform.kernel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * A name no environment sets. Long and specific rather than random, because a test that generates
 * its input cannot say what it ran on when it fails.
 */
private const val ABSENT = "TALLYVANE_A_VARIABLE_NO_ENVIRONMENT_WOULD_EVER_DEFINE"

class EnvironmentProcessSpec :
    StringSpec(
        {
            // Deliberately arranged from the environment *map*, which is a different call than the
            // single-key lookup under test. It is the closest to an independent observation a
            // wrapper this thin allows, and what it really pins is that the port reads the
            // environment and not, say, system properties — the plausible mistake here. Windows
            // exposes PATH and Path case-insensitively but can give the map and single-key lookup
            // different normalized values, so this comparison uses another process variable.
            "reads a variable the process really has" {
                val (name, value) = System.getenv().entries.first { (key, _) ->
                    !key.equals("PATH", ignoreCase = true)
                }

                Environment.Process().read(name) shouldBe value
            }

            "answers null for a name the process does not have" {
                Environment.Process().read(ABSENT).shouldBeNull()
            }
        },
    )
