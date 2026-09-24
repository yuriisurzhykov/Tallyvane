package tallyvane.arch

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe

class ArchitectureRulesSpec :
    StringSpec(
        {
            ARCH_RULES.forEach { rule ->
                "${rule.id} — production is clean" {
                    rule.violations(rule.scope()) shouldBe emptyList()
                }
                "${rule.id} — fixture is dirty" {
                    rule.violations(fixtureScope(rule.id)).shouldNotBeEmpty()
                }
            }
            "identity-web-behavior-is-interface — data contracts and one-word nested implementations are clean" {
                identityWebBehaviorIsInterface(fixtureScope("identity-web-behavior-clean")) shouldBe emptyList()
            }
        },
    )
