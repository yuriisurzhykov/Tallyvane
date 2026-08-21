/**
 * DS001, inline `style={{}}` side, dimension/typography.
 *
 * Fail-closed: a named constant, `N + "px"`, `mergeStyle`, or an extracted
 * style object is still a hardcoded dimension. The replacement is a
 * design-token resource (`var(--…)`, `calc()`/`clamp()`, or a Tailwind
 * class), not another constant. Runtime values (a parameter, a call, a
 * member) stay allowed. The only silence is a complete
 * `@architecture-exception` directive naming this rule and an ADR.
 */
import type { Rule } from "eslint";
import { hasArchitectureException } from "./architecture-exception.ts";
import { isStyleAttribute, propertyKeyName } from "./ast-helpers.ts";
import { isDimensionPropertyKey, isHardcodedCssLength, REACT_PX_PROPERTY, UNIT_TOKEN } from "./css-length.ts";
import { isUnitStringLiteral, resolveInit, type ResolvedInit } from "./resolve-init.ts";

const RULE_NAME = "no-raw-dimension-value";

const MESSAGE =
    'Inline style property "{{property}}" has a hardcoded dimension ({{value}}). Use a design-token resource (`var(--…)` or a Tailwind class), not a hardcoded length — a named constant or `n + "px"` is still a hardcoded value.';

function report(context: Rule.RuleContext, node: any, property: string, value: string): void {
    if (hasArchitectureException(context, node, RULE_NAME)) return;
    context.report({ node, messageId: "rawDimensionInInlineStyle", data: { property, value } });
}

function describeResolved(resolved: ResolvedInit): string {
    if (resolved.kind === "literal") return JSON.stringify(resolved.value);
    if (resolved.kind === "unknown") return "unresolved identifier";
    return resolved.kind;
}

function templateGluesUnit(node: any): boolean {
    return node.quasis.some((quasi: { value: { raw: string } }, index: number) => index > 0 && UNIT_TOKEN.test(quasi.value.raw.trim()));
}

function checkLiteral(context: Rule.RuleContext, keyName: string, node: any): void {
    if (typeof node.value === "string" && isHardcodedCssLength(node.value)) {
        report(context, node, keyName, node.value.trim());
        return;
    }
    if (typeof node.value === "number" && node.value !== 0 && REACT_PX_PROPERTY.test(keyName)) {
        report(context, node, keyName, String(node.value));
    }
}

function interpolationsAreRuntime(context: Rule.RuleContext, node: any): boolean {
    return node.expressions.every((expression: any) => resolveInit(context, expression).kind === "runtime");
}

function checkTemplate(context: Rule.RuleContext, keyName: string, node: any): void {
    const raw: string = node.quasis.map((quasi: { value: { raw: string } }) => quasi.value.raw).join("");
    if (node.expressions.length === 0) {
        if (isHardcodedCssLength(raw)) report(context, node, keyName, raw.trim());
        return;
    }
    if (interpolationsAreRuntime(context, node)) return;
    const trimmed = raw.trim();
    report(context, node, keyName, templateGluesUnit(node) ? `${raw} + unit` : (trimmed === "" ? "interpolated constant" : trimmed));
}

function checkBinary(context: Rule.RuleContext, keyName: string, node: any): void {
    if (node.operator !== "+") return;
    const unitOnRight = isUnitStringLiteral(node.right);
    const unitOnLeft = isUnitStringLiteral(node.left);
    if (!unitOnRight && !unitOnLeft) return;
    const other = unitOnRight ? node.left : node.right;
    const resolved = resolveInit(context, other);
    if (resolved.kind === "runtime") return;
    report(context, node, keyName, `${describeResolved(resolved)} + unit`);
}

function checkResolved(context: Rule.RuleContext, keyName: string, node: any, resolved: ResolvedInit): void {
    if (resolved.kind === "runtime") return;
    if (resolved.kind === "object") {
        checkStyleObject(context, resolved.node);
        return;
    }
    if (resolved.kind === "literal") {
        if (typeof resolved.value === "string") {
            if (isHardcodedCssLength(resolved.value)) report(context, node, keyName, resolved.value);
            return;
        }
        if (resolved.value !== 0 && REACT_PX_PROPERTY.test(keyName)) {
            report(context, node, keyName, String(resolved.value));
        }
        return;
    }
    report(context, node, keyName, describeResolved(resolved));
}

const VALUE_CHECKERS: Record<string, (context: Rule.RuleContext, keyName: string, node: any) => void> = {
    Literal(context, keyName, node) {
        checkLiteral(context, keyName, node);
    },
    TemplateLiteral(context, keyName, node) {
        checkTemplate(context, keyName, node);
    },
    BinaryExpression(context, keyName, node) {
        checkBinary(context, keyName, node);
    },
    Identifier(context, keyName, node) {
        checkResolved(context, keyName, node, resolveInit(context, node));
    },
    CallExpression(context, _keyName, node) {
        for (const argument of node.arguments) walkStyleExpression(context, argument);
    },
    TSAsExpression(context, keyName, node) {
        checkStyleValue(context, keyName, node.expression);
    },
    ObjectExpression(context, _keyName, node) {
        checkStyleObject(context, node);
    },
    ConditionalExpression(context, keyName, node) {
        checkStyleValue(context, keyName, node.consequent);
        checkStyleValue(context, keyName, node.alternate);
    },
    LogicalExpression(context, keyName, node) {
        checkStyleValue(context, keyName, node.left);
        checkStyleValue(context, keyName, node.right);
    },
};

function checkStyleValue(context: Rule.RuleContext, keyName: string, node: any): void {
    if (!node) return;
    const checker = VALUE_CHECKERS[node.type];
    if (checker) {
        checker(context, keyName, node);
        return;
    }
    const resolved = resolveInit(context, node);
    if (resolved.kind === "runtime") return;
    checkResolved(context, keyName, node, resolved);
}

function checkStyleProperty(context: Rule.RuleContext, property: any): void {
    if (property.type === "SpreadElement") {
        walkStyleExpression(context, property.argument);
        return;
    }
    if (property.type !== "Property") return;
    const keyName = propertyKeyName(property.key);
    if (!keyName || !isDimensionPropertyKey(keyName)) return;
    checkStyleValue(context, keyName, property.value);
}

function checkStyleObject(context: Rule.RuleContext, object: any): void {
    if (object?.type !== "ObjectExpression") return;
    for (const property of object.properties) checkStyleProperty(context, property);
}

function walkStyleExpression(context: Rule.RuleContext, node: any): void {
    if (!node) return;
    if (node.type === "ObjectExpression") {
        checkStyleObject(context, node);
        return;
    }
    if (node.type === "TSAsExpression") {
        walkStyleExpression(context, node.expression);
        return;
    }
    if (node.type === "CallExpression") {
        for (const argument of node.arguments) walkStyleExpression(context, argument);
        return;
    }
    if (node.type === "ConditionalExpression") {
        walkStyleExpression(context, node.consequent);
        walkStyleExpression(context, node.alternate);
        return;
    }
    if (node.type === "LogicalExpression") {
        walkStyleExpression(context, node.left);
        walkStyleExpression(context, node.right);
        return;
    }
    if (node.type === "Identifier") {
        const resolved = resolveInit(context, node);
        if (resolved.kind === "object") checkStyleObject(context, resolved.node);
        else if (resolved.kind !== "runtime") {
            // An identifier used as the whole `style={x}` value — inspect as if it were a bag of properties only when it resolved to an object. Non-objects at this position are not a dimension property.
        }
    }
}

const rule: Rule.RuleModule = {
    meta: {
        type: "problem",
        docs: {
            description:
                "Disallow a hardcoded dimension in inline style={{}} — including named constants and n+unit concatenation. Use a CSS variable backed by a design token, or a Tailwind class. calc()/clamp()/var() and runtime values are exempt; a complete @architecture-exception is the only other silence.",
        },
        schema: [],
        messages: {
            rawDimensionInInlineStyle: MESSAGE,
        },
    },
    create(context) {
        return {
            JSXAttribute(node: any) {
                if (!isStyleAttribute(node)) return;
                const value = node.value;
                if (value?.type !== "JSXExpressionContainer") return;
                walkStyleExpression(context, value.expression);
            },
        };
    },
};

export default rule;
