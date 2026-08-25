package tallyvane.gradle.ktlint

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId

/**
 * ServiceLoader entry for the `tallyvane` ktlint ruleset.
 *
 * Holds no checking logic: each [RuleProvider] constructs one rule. ktlint
 * discovers this class from
 * `META-INF/services/com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3`.
 */
class TallyvaneRuleSetProvider : RuleSetProviderV3(RuleSetId(TALLYVANE_RULE_SET_ID)) {
    override fun getRuleProviders(): Set<RuleProvider> =
        setOf(
            RuleProvider { NoSingleLineKdocRule() },
        )
}
