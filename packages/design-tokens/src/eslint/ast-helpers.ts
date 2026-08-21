/**
 * Shared AST helpers for the JSX/Tailwind-facing DS001 rules
 * (`no-arbitrary-color-class.ts`, `no-arbitrary-dimension-class.ts`) and
 * the inline-`style={{}}` ones (`no-raw-color-value.ts`,
 * `no-raw-dimension-value.ts`) — extracted once both categories existed,
 * to avoid a third/fourth copy of the same tree-walking logic. AST nodes
 * are typed loosely (`any`) throughout, deliberately — `estree`'s precise
 * expression unions fight a generic recursive walker far more than they
 * help here, the same pragmatic choice most hand-written ESLint rules make.
 */

export function isClassNameAttribute(node: any): boolean {
    return node.type === "JSXAttribute" && node.name?.name === "className";
}

export function isStyleAttribute(node: any): boolean {
    return node.type === "JSXAttribute" && node.name?.name === "style";
}

export function propertyKeyName(key: any): string | null {
    if (key.type === "Identifier") return key.name;
    if (key.type === "Literal" && typeof key.value === "string") return key.value;
    return null;
}

type StringVisitor = (value: string, node: any) => void;
type NodeWalker = (node: any, visit: StringVisitor) => void;

/**
 * One small function per node shape instead of a `switch` — each is simple
 * enough on its own to stay well under the complexity budget, and a new
 * shape (should `walkForStrings` ever need one) is one added map entry, not
 * a growing branch inside a single function.
 */
const WALKERS: Record<string, NodeWalker> = {
    Literal(node, visit) {
        if (typeof node.value === "string") visit(node.value, node);
    },
    JSXExpressionContainer(node, visit) {
        walkForStrings(node.expression, visit);
    },
    TemplateLiteral(node, visit) {
        for (const quasi of node.quasis) visit(quasi.value.raw, node);
    },
    CallExpression(node, visit) {
        for (const arg of node.arguments) walkForStrings(arg, visit);
    },
    ConditionalExpression(node, visit) {
        walkForStrings(node.consequent, visit);
        walkForStrings(node.alternate, visit);
    },
    LogicalExpression(node, visit) {
        walkForStrings(node.left, visit);
        walkForStrings(node.right, visit);
    },
    ArrayExpression(node, visit) {
        for (const element of node.elements) walkForStrings(element, visit);
    },
    ObjectExpression(node, visit) {
        for (const property of node.properties) {
            if (property.type === "Property") walkForStrings(property.key, visit);
        }
    },
};

const TEMPLATE_WALKERS: Record<string, (node: any, visit: (template: any) => void) => void> = {
    JSXExpressionContainer(node, visit) {
        walkForTemplateLiterals(node.expression, visit);
    },
    CallExpression(node, visit) {
        for (const arg of node.arguments) walkForTemplateLiterals(arg, visit);
    },
    ConditionalExpression(node, visit) {
        walkForTemplateLiterals(node.consequent, visit);
        walkForTemplateLiterals(node.alternate, visit);
    },
    LogicalExpression(node, visit) {
        walkForTemplateLiterals(node.left, visit);
        walkForTemplateLiterals(node.right, visit);
    },
    ArrayExpression(node, visit) {
        for (const element of node.elements) walkForTemplateLiterals(element, visit);
    },
};

/** Walks a `className={...}` value for every string literal it could resolve to — through `cn()`/`clsx()`-style calls, ternaries, `&&`, arrays, and template literals. */
export function walkForStrings(node: any, visit: StringVisitor): void {
    if (!node) return;
    WALKERS[node.type]?.(node, visit);
}

/** Same tree as `walkForStrings`, but yields each TemplateLiteral node so a rule can see constructed `` `w-[${n}px]` `` holes that no single quasi matches. */
export function walkForTemplateLiterals(node: any, visit: (template: any) => void): void {
    if (!node) return;
    if (node.type === "TemplateLiteral") {
        visit(node);
        return;
    }
    TEMPLATE_WALKERS[node.type]?.(node, visit);
}
