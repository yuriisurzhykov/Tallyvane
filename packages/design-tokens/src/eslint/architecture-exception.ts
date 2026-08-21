/**
 * Reads the frontend `@architecture-exception` directive (ARCHITECTURE.md
 * §15.7). Same three fields as the backend annotation: `rule`, `adr`,
 * `reason`. A missing `adr=` is not an exception — that is how an agent
 * would otherwise invent a comment to silence the check.
 */
import type { Rule } from "eslint";

const DIRECTIVE = /@architecture-exception\b/;
const RULE_FIELD = /\brule=([^\s]+)/;
const ADR_FIELD = /\badr=(ADR-\d+)/;

const STOP_AT = new Set([
    "FunctionDeclaration",
    "FunctionExpression",
    "ArrowFunctionExpression",
    "Program",
]);

function commentsExempt(context: Rule.RuleContext, node: any, ruleName: string): boolean {
    const text = context.sourceCode.getCommentsBefore(node).map((comment) => comment.value).join("\n");
    if (!DIRECTIVE.test(text)) return false;
    const rule = RULE_FIELD.exec(text)?.[1];
    const adr = ADR_FIELD.exec(text)?.[1];
    return rule === ruleName && adr !== undefined;
}

/** True when `node` or a close ancestor carries a complete `@architecture-exception` for `ruleName`. */
export function hasArchitectureException(context: Rule.RuleContext, node: any, ruleName: string): boolean {
    const ancestors = context.sourceCode.getAncestors(node);
    const chain = [...ancestors, node];
    for (let index = chain.length - 1; index >= 0; index -= 1) {
        const candidate = chain[index];
        if (!candidate) continue;
        if (STOP_AT.has(candidate.type) && candidate !== node) break;
        if (commentsExempt(context, candidate, ruleName)) return true;
    }
    return false;
}
