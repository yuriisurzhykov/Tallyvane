package tallyvane.arch

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty

class ArchitectureRulesSpec :
    StringSpec(
        {
            ARCH_RULES.forEach { rule ->
                "${rule.id} — production is clean" {
                    rule.violations(rule.scope()).shouldBeEmpty()
                }
                "${rule.id} — fixture is dirty" {
                    rule.violations(fixtureScope(rule.id)).shouldNotBeEmpty()
                }
            }
        },
    )
