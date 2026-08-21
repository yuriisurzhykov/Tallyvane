/**
 * Lucide's `size={16}` is a pixel dimension that `no-raw-dimension-value`
 * never sees (it is an SVG attribute, not a CSS style). Same rule as the
 * rest of DS001: the size is `--control-icon` (or a component token),
 * applied as `h-(--control-icon) w-(--control-icon)`, not a number.
 *
 * Scoped to names imported from `lucide-react` so `Button size="sm"` and
 * `Input size="md"` stay untouched.
 */
import type { Rule } from "eslint";
import { hasArchitectureException } from "./architecture-exception.ts";
import { resolveInit } from "./resolve-init.ts";

const RULE_NAME = "no-raw-icon-size";
const LUCIDE = "lucide-react";

function attributeName(node: any): string | null {
    return node?.name?.type === "JSXIdentifier" ? node.name.name : null;
}

function sizeValueNode(attribute: any) {
    const value = attribute.value;
    if (!value) return undefined;
    if (value.type === "JSXExpressionContainer") return value.expression;
    if (value.type === "Literal") return value;
    return undefined;
}

function describeSize(node: any, context: Rule.RuleContext): string | null {
    if (node.type === "Literal" && typeof node.value === "number") return String(node.value);
    if (node.type === "Identifier") {
        const resolved = resolveInit(context, node);
        if (resolved.kind === "runtime") return null;
        if (resolved.kind === "literal" && typeof resolved.value === "number") return String(resolved.value);
        if (resolved.kind === "literal") return JSON.stringify(resolved.value);
        return "unresolved identifier";
    }
    const resolved = resolveInit(context, node);
    if (resolved.kind === "runtime") return null;
    if (resolved.kind === "literal" && typeof resolved.value === "number") return String(resolved.value);
    if (resolved.kind === "unknown" || resolved.kind === "literal") return "non-token size";
    return null;
}

const rule: Rule.RuleModule = {
    meta: {
        type: "problem",
        docs: {
            description:
                "Disallow lucide-react size={n} / size={CONST} — size the glyph with h-(--control-icon) w-(--control-icon) (or a component token), not a hardcoded pixel number.",
        },
        schema: [],
        messages: {
            rawIconSize:
                'Lucide icon size={{{value}}} is a hardcoded dimension. Use h-(--control-icon) w-(--control-icon) (or a component-token equivalent), not size={n} — a named constant is still a hardcoded value.',
        },
    },
    create(context) {
        const lucideLocals = new Set<string>();
        const lucideNamespaces = new Set<string>();

        function isLucideElement(opening: any): boolean {
            const name = opening.name;
            if (name?.type === "JSXIdentifier") return lucideLocals.has(name.name);
            if (name?.type === "JSXMemberExpression" && name.object?.type === "JSXIdentifier") {
                return lucideNamespaces.has(name.object.name);
            }
            return false;
        }

        return {
            ImportDeclaration(node: any) {
                if (node.source?.value !== LUCIDE) return;
                for (const specifier of node.specifiers) {
                    if (specifier.type === "ImportSpecifier") lucideLocals.add(specifier.local.name);
                    if (specifier.type === "ImportNamespaceSpecifier") lucideNamespaces.add(specifier.local.name);
                }
            },
            JSXOpeningElement(node: any) {
                if (!isLucideElement(node)) return;
                for (const attribute of node.attributes) {
                    if (attribute.type !== "JSXAttribute" || attributeName(attribute) !== "size") continue;
                    const valueNode = sizeValueNode(attribute);
                    if (!valueNode) continue;
                    const value = describeSize(valueNode, context);
                    if (value === null) continue;
                    if (hasArchitectureException(context, valueNode, RULE_NAME)) continue;
                    context.report({ node: valueNode, messageId: "rawIconSize", data: { value } });
                }
            },
        };
    },
};

export default rule;
