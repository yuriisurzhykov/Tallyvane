package tallyvane.arch

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe

class ArchitectureRulesSpec :
    StringSpec(
        {
            val sourceScopes = mapOf(
                ArchScope.Production to lazy(::productionScope),
                ArchScope.Code to lazy(::codeScope),
            )
            ARCH_RULES.forEach { rule ->
                "${rule.id} — production is clean" {
                    rule.violations(sourceScopes.getValue(rule.scope).value) shouldBe emptyList()
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
