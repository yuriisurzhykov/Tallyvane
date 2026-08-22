package tallyvane.gradle.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.KDOC
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.indent
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.TreeElement

/**
 * Rejects a KDoc that occupies a single line.
 *
 * Official ktlint (`kdoc-wrapping`) only forbids a KDoc sharing a line with
 * other code. A one-line KDoc on its own line is legal there. This rule
 * still wraps it so the opener, the starred text, and the closer each
 * occupy their own line. The closer is the usual terminator, not a starred
 * copy of it.
 *
 * Style official ktlint does not cover lives in this ruleset, not in detekt
 * and not in Konsist (ADR-043).
 */
class NoSingleLineKdocRule :
    Rule(
        ruleId = RuleId("$TALLYVANE_RULE_SET_ID:no-single-line-kdoc"),
        about = About(
            maintainer = "tallyvane",
            repositoryUrl = "backend/build-logic/ktlint-rules",
            issueTrackerUrl = "backend/build-logic/ktlint-rules/README.md",
        ),
    ),
    RuleAutocorrectApproveHandler {

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        if (node.elementType != KDOC || node.textContains('\n')) {
            return
        }
        emit(node.startOffset, MESSAGE, true).ifAutocorrectAllowed {
            // PSI replace needs IntelliJ treeCopyHandler, which ktlint-test does not install.
            require(node is TreeElement)
            node.rawReplaceWithList(
                LazyParseablePsiElement(KDOC, wrap(node.text, node.indent(includeNewline = false))),
            )
        }
    }

    private companion object {
        const val MESSAGE: String = "A KDoc comment must use the multiline form"

        fun wrap(
            text: String,
            indent: String,
        ): String {
            val inner = text.removePrefix("/**").removeSuffix("*/").trim()
            val body = if (inner.isEmpty()) " *" else " * $inner"
            return "/**\n$indent$body\n$indent */"
        }
    }
}
