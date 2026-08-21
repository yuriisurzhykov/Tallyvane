/**
 * Resolves an expression to a compile-time init, a runtime value, or
 * "unknown" (imported / unbound — fail-closed). Scope lookup only: following
 * a relative import would need a TypeScript parse of another file, and
 * fail-closed on imports already catches `import { WIDTH } from "./tokens"`
 * holding `"26px"`.
 */
import type { Rule } from "eslint";

export type ResolvedInit =
    | { readonly kind: "literal"; readonly value: string | number }
    | { readonly kind: "runtime" }
    | { readonly kind: "object"; readonly node: any }
    | { readonly kind: "unknown" };

const RUNTIME_TYPES = new Set([
    "CallExpression",
    "MemberExpression",
    "OptionalMemberExpression",
    "OptionalCallExpression",
    "NewExpression",
    "AwaitExpression",
    "ThisExpression",
]);

function findVariable(context: Rule.RuleContext, identifier: any) {
    return lookupScope(context.sourceCode.getScope(identifier), identifier.name);
}

function lookupScope(scope: { set: { get: (name: string) => unknown }; upper?: unknown }, name: string): any {
    const found = scope.set.get(name);
    if (found) return found;
    if (scope.upper === undefined || scope.upper === null) return undefined;
    return lookupScope(scope.upper as typeof scope, name);
}

function isParameter(variable: any): boolean {
    return variable.defs.some((def: { type: string }) => def.type === "Parameter");
}

function isImport(variable: any): boolean {
    return variable.defs.some((def: { type: string }) => def.type === "ImportBinding");
}

function variableInit(variable: any) {
    const def = variable.defs.find((entry: { type: string; node?: { init?: any } }) => entry.type === "Variable");
    return def?.node?.init;
}

function resolveBinary(context: Rule.RuleContext, node: any, seen: Set<object>): ResolvedInit {
    const left = resolveInit(context, node.left, seen);
    const right = resolveInit(context, node.right, seen);
    if (left.kind === "runtime" || right.kind === "runtime") return { kind: "runtime" };
    if (left.kind === "literal" && right.kind === "literal") return left;
    return { kind: "unknown" };
}

function resolveLiteralNode(node: any): ResolvedInit | undefined {
    if (node.type === "Literal") {
        if (typeof node.value === "string" || typeof node.value === "number") {
            return { kind: "literal", value: node.value };
        }
        return { kind: "unknown" };
    }
    if (node.type === "UnaryExpression" && node.operator === "-" && node.argument?.type === "Literal" && typeof node.argument.value === "number") {
        return { kind: "literal", value: -node.argument.value };
    }
    return undefined;
}

function resolveIdentifier(context: Rule.RuleContext, node: any, seen: Set<object>): ResolvedInit {
    if (node.name === "undefined") return { kind: "runtime" };
    const variable = findVariable(context, node);
    if (!variable) return { kind: "unknown" };
    if (isParameter(variable)) return { kind: "runtime" };
    if (isImport(variable)) return { kind: "unknown" };
    const init = variableInit(variable);
    if (!init) return { kind: "unknown" };
    return resolveInit(context, init, seen);
}

/** Resolves `node` through same-file const bindings. `seen` breaks cycles. */
export function resolveInit(context: Rule.RuleContext, node: any, seen: Set<object> = new Set<object>()): ResolvedInit {
    if (!node || seen.has(node)) return { kind: "unknown" };
    seen.add(node);
    if (RUNTIME_TYPES.has(node.type)) return { kind: "runtime" };
    const literal = resolveLiteralNode(node);
    if (literal) return literal;
    if (node.type === "ObjectExpression") return { kind: "object", node };
    if (node.type === "TSAsExpression" || node.type === "TSSatisfiesExpression" || node.type === "ChainExpression") {
        return resolveInit(context, node.expression ?? node.argument, seen);
    }
    if (node.type === "BinaryExpression") return resolveBinary(context, node, seen);
    if (node.type === "Identifier") return resolveIdentifier(context, node, seen);
    return { kind: "unknown" };
}

export function isUnitStringLiteral(node: any): boolean {
    return node?.type === "Literal" && typeof node.value === "string" && /^(px|rem|em|vh|vw|vmin|vmax|%)$/.test(node.value);
}
