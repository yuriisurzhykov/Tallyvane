/**
 * The build-time-only orchestrator: assembles a registry per theme,
 * validates the whole graph (DS002/DS006/DS101/DS102/DS201/DS202), then
 * resolves and serializes. Nothing in this file is imported by anything
 * the app ships at runtime — see the plan's "adapters shouldn't re-run the
 * compiler at runtime" audit finding. A project's own
 * `scripts/generate-design-tokens.ts` is this function's only real caller.
 *
 * DS003 (illegal dependency direction — primitives must never reference a
 * theme/semantic/component/composite) has no runtime code here at all: a
 * `tokens/*.ts` file importing from `themes/`/`components/` would be a real
 * TypeScript circular-import error the moment it happened, so the type
 * system enforces it before this function ever runs.
 */
import { resolveTree } from "./references";
import { flattenScalars, cssVariableName } from "./serializers/css-value";
import { serializeGradient, validateGradientStops, type Gradient } from "./serializers/gradient";
import { serializeShadow, type ShadowLayer } from "./serializers/shadow";
import { validateTransitions, type Transition } from "./serializers/transition";
import {
    findPrimitiveBoundaryCrossings,
    findSingleConsumerGlobals,
    findUnusedGlobalSemantics,
    type NamespacedTree,
} from "./usage-graph";
import {
    checkOptionalKeyParity,
    validateColorFieldsDeep,
    validateColorPrimitiveFormat,
    validateNoRawColorLiterals,
    validateReferences,
    validateUniqueVariableNames,
} from "./validate";
import type { ComponentLayer, CompositeLayer, Contract, PrimitiveLayer, SemanticLayer, TokenTree } from "./types";

export interface CompilerInput {
    /** category name -> primitive tree, e.g. `{ color, dimension, radius, typography, motion }`. */
    readonly primitives: Readonly<Record<string, PrimitiveLayer<TokenTree>>>;
    /** category name -> the contract that category's semantic layer(s) satisfy — used to keep DS101 from flagging every required role as "unused" (required roles are meant to be consumed by the Tailwind adapter, not by another token). */
    readonly contracts: Readonly<Record<string, Contract<string>>>;
    /** theme name -> category name -> semantic tree — only categories WITH a theme axis belong here (color, for this project). */
    readonly themes: Readonly<Record<string, Readonly<Record<string, SemanticLayer<TokenTree>>>>>;
    /** category name -> semantic tree — categories with NO theme axis (radius, spacing, motion, typography). */
    readonly flatSemantics: Readonly<Record<string, SemanticLayer<TokenTree>>>;
    readonly components: readonly ComponentLayer<TokenTree>[];
    readonly composites: readonly CompositeLayer<TokenTree>[];
}

export interface CompileResult {
    readonly css: string;
    /**
     * Primitive category -> the CSS variable names that tier emitted.
     *
     * Exposed so a project can enforce that its Tailwind adapter never hands a
     * primitive a class-facing name. That rule cannot be checked by reading the
     * adapter alone: `--ds-color-surface-page` (a role) and
     * `--ds-color-neutral-700` (a primitive) are indistinguishable by shape,
     * and only the compiler knows which is which.
     */
    readonly primitiveVariables: Readonly<Record<string, readonly string[]>>;
    /** theme name -> fully-resolved plain data (no `{ref}` strings left) — the ONLY thing adapters (Mermaid/OG/WebGL) may import. */
    readonly resolved: Readonly<Record<string, ResolvedThemeData>>;
    readonly warnings: readonly string[];
}

export interface ResolvedThemeData {
    readonly color: Readonly<Record<string, unknown>>;
    readonly component: Readonly<Record<string, unknown>>;
    /**
     * Composite kind -> name -> resolved value. One bucket per kind rather than
     * a named field each, so adding a kind is a change to the token source
     * alone and never to this interface.
     *
     * A gradient, shadow or transition arrives as a finished CSS string; every
     * other kind keeps its tree, since it has no single-value form to collapse
     * into.
     */
    readonly composite: Readonly<Record<string, Readonly<Record<string, unknown>>>>;
}

export class DesignTokenBuildError extends Error {}

function buildRegistry(input: CompilerInput, themeName: string): Record<string, unknown> {
    return {
        ...input.primitives,
        theme: input.themes[themeName] ?? {},
        semantic: input.flatSemantics,
    };
}

function collectConsumers(input: CompilerInput): NamespacedTree[] {
    return [
        ...input.components.map((tree) => ({ namespace: `component:${tree.__namespace}`, tree })),
        ...input.composites.map((tree) => ({ namespace: `composite:${tree.__compositeKind}`, tree })),
    ];
}

function requiredGlobalPaths(input: CompilerInput): Set<string> {
    const paths = new Set<string>();
    for (const [themeName, categories] of Object.entries(input.themes)) {
        for (const category of Object.keys(categories)) {
            for (const role of input.contracts[category]?.required ?? []) {
                paths.add(`theme.${category}.${role}`);
            }
        }
        void themeName; // required-ness doesn't vary by theme; iterated for clarity only.
    }
    for (const category of Object.keys(input.flatSemantics)) {
        for (const role of input.contracts[category]?.required ?? []) {
            paths.add(`semantic.${category}.${role}`);
        }
    }
    return paths;
}

function definedGlobalPaths(input: CompilerInput): string[] {
    const paths: string[] = [];
    for (const categories of Object.values(input.themes)) {
        for (const [category, roles] of Object.entries(categories)) {
            for (const role of Object.keys(roles)) {
                if (!role.startsWith("__")) paths.push(`theme.${category}.${role}`);
            }
        }
    }
    for (const [category, roles] of Object.entries(input.flatSemantics)) {
        for (const role of Object.keys(roles)) {
            if (!role.startsWith("__")) paths.push(`semantic.${category}.${role}`);
        }
    }
    return [...new Set(paths)];
}

/** Runs every graph-wide check (DS002, DS006, DS101 warn, DS102/DS201/DS202 error). Throws on the first error-level violation; returns accumulated warnings otherwise. */
export function validateDesignTokens(input: CompilerInput): { readonly warnings: readonly string[] } {
    const warnings: string[] = [];
    const consumers = collectConsumers(input);

    // DS001, color-specific for this pass — see README.md's dated entry for
    // why this wasn't wired in until now. Both validators no-op on a
    // missing/undefined category, so no "does this project have color?" guard.
    validateColorPrimitiveFormat(input.primitives.color);
    for (const [themeName, byCategory] of Object.entries(input.themes)) {
        validateNoRawColorLiterals(byCategory.color, ["theme", themeName, "color"]);
    }
    for (const component of input.components) {
        validateNoRawColorLiterals(component, ["component", component.__namespace]);
    }
    for (const composite of input.composites) {
        validateColorFieldsDeep(composite, ["composite", composite.__compositeKind]);
    }

    const roots: TokenTree[] = [...Object.values(input.flatSemantics), ...input.components, ...input.composites];
    for (const [themeName, byCategory] of Object.entries(input.themes)) {
        const registry = buildRegistry(input, themeName);
        validateReferences(registry, [...roots, ...Object.values(byCategory)]);
    }

    const categories = new Set(Object.values(input.themes).flatMap((c) => Object.keys(c)));
    // The role names to leave out of the parity check, taken straight from the
    // contracts of the categories being checked. This used to re-derive them by
    // splitting the dotted paths `requiredGlobalPaths` had just assembled,
    // which also folded in names required by flat categories that no theme has
    // — enough to silence a parity warning about a genuinely lopsided optional
    // role, purely because some unrelated category required the same name.
    const required = new Set<string>();
    for (const category of categories) {
        for (const role of input.contracts[category]?.required ?? []) required.add(role);
    }
    for (const category of categories) {
        const perTheme: Record<string, TokenTree> = {};
        for (const [themeName, byCategory] of Object.entries(input.themes)) {
            const roles = byCategory[category];
            if (roles) perTheme[themeName] = roles;
        }
        warnings.push(...checkOptionalKeyParity(perTheme, required));
    }

    const crossings = findPrimitiveBoundaryCrossings(consumers);
    if (crossings.length > 0) {
        throw new DesignTokenBuildError(
            crossings
                .map(
                    (crossing) =>
                        `DS201 Primitive "${crossing.primitivePath}" crosses component/composite domain boundaries.\n` +
                        `  Consumers:\n${crossing.consumers.map((c) => `    - ${c}`).join("\n")}\n` +
                        "  Decide: promote to a global-semantic role, OR keep both as independent tokens if this is coincidence, not shared meaning.",
                )
                .join("\n\n"),
        );
    }

    // Same exclusion as DS101 below, for the same reason: a REQUIRED role's real
    // reason to be global is that Tailwind/JSX consume it directly across many
    // files — invisible to this token-to-token graph — so having only one
    // component/composite reference it in the graph doesn't mean it should be
    // demoted; an OPTIONAL role with a single consumer very much does.
    const requiredPathsForDS102 = requiredGlobalPaths(input);
    const singleConsumers = findSingleConsumerGlobals(consumers).filter((violation) => !requiredPathsForDS102.has(violation.semanticPath));
    if (singleConsumers.length > 0) {
        throw new DesignTokenBuildError(
            singleConsumers
                .map(
                    (violation) =>
                        `DS102 Global-semantic token "{${violation.semanticPath}}" is consumed by only one namespace: "${violation.consumer}".\n` +
                        "  Move it to a component token instead, and reference a primitive directly.",
                )
                .join("\n\n"),
        );
    }

    const requiredPaths = requiredGlobalPaths(input);
    const unused = findUnusedGlobalSemantics(
        definedGlobalPaths(input).filter((path) => !requiredPaths.has(path)),
        consumers,
    );
    warnings.push(
        ...unused.map(
            (path) =>
                `DS101 Optional global-semantic token "{${path}}" is referenced by no component/composite token. ` +
                "(Best-effort signal only — a REQUIRED role consumed exclusively through the Tailwind adapter never triggers this.)",
        ),
    );

    return { warnings };
}

/**
 * Turns every composite into CSS declarations plus the resolved data adapters
 * read.
 *
 * Three kinds collapse to a single CSS value each, because the property they
 * feed accepts one: a gradient, a shadow stack, a transition. Any other kind
 * has no such form — a text style is four unrelated properties — so its leaves
 * each become their own variable. That fallback is what makes a new kind
 * usable the moment a project defines one, instead of validating cleanly and
 * then emitting nothing at all, which is a far worse failure than an error:
 * everything appears to work until the styles are simply absent.
 */
function serializeCompositesFor(
    composites: readonly CompositeLayer<TokenTree>[],
    registry: Record<string, unknown>,
): { lines: string[]; byKind: Record<string, Record<string, unknown>> } {
    const lines: string[] = [];
    const byKind: Record<string, Record<string, unknown>> = {};

    for (const composite of composites) {
        const kind = composite.__compositeKind;
        const resolved = resolveTree(composite, registry);
        const entries = (byKind[kind] ??= {});

        const single = serializeSingleValued(kind, resolved);
        if (single) {
            for (const [name, value] of Object.entries(single)) {
                entries[name] = value;
                lines.push(`    ${cssVariableName([kind], [name])}: ${value};`);
            }
            continue;
        }

        // Validated but not collapsed. A transition deliberately stays two
        // variables: a duration and an easing can always be composed into the
        // `transition` shorthand at the point of use, while a shorthand cannot
        // be taken apart — so the split form is the one that leaves the caller
        // free to also choose WHICH property is animated.
        if (kind === "transition") validateTransitions(resolved as unknown as Record<string, Transition>);

        for (const [name, value] of Object.entries(resolved)) entries[name] = value;
        for (const [path, value] of flattenScalars(resolved)) {
            lines.push(`    ${cssVariableName([kind], path)}: ${value};`);
        }
    }
    return { lines, byKind };
}

/** The kinds with a one-value CSS form. Returns `null` for anything else, which is the signal to fall back to per-leaf variables. */
function serializeSingleValued(kind: string, resolved: TokenTree): Record<string, string> | null {
    if (kind === "gradient") {
        const gradients = resolved as unknown as Record<string, Gradient>;
        validateGradientStops(gradients);
        return mapValues(gradients, serializeGradient);
    }
    if (kind === "shadow") {
        return mapValues(resolved as unknown as Record<string, readonly ShadowLayer[]>, serializeShadow);
    }
    return null;
}

function mapValues<T>(source: Readonly<Record<string, T>>, serialize: (value: T) => string): Record<string, string> {
    return Object.fromEntries(Object.entries(source).map(([name, value]) => [name, serialize(value)]));
}

function serializeComponentsFor(components: readonly ComponentLayer<TokenTree>[], registry: Record<string, unknown>): { lines: string[]; data: Record<string, unknown> } {
    const lines: string[] = [];
    const data: Record<string, unknown> = {};
    for (const component of components) {
        const resolved = resolveTree(component, registry);
        data[component.__namespace] = resolved;
        for (const [path, value] of flattenScalars(resolved)) {
            lines.push(`    ${cssVariableName(["component", component.__namespace], path)}: ${value};`);
        }
    }
    return { lines, data };
}

function printFlatDeclarations(input: CompilerInput): { lines: string[]; primitiveNames: string[]; primitivesByCategory: Record<string, string[]> } {
    const lines: string[] = [];
    const names: string[] = [];
    // Kept per category as well as flat, because a caller cannot recover this
    // from the variable names afterwards: a colour PRIMITIVE and a colour ROLE
    // both compile to `--ds-color-*` (see the note below on why the theme tier
    // carries no prefix), so "is this name a primitive" is answerable only
    // here, where the two are still distinguishable.
    const primitivesByCategory: Record<string, string[]> = {};
    for (const [category, tree] of Object.entries(input.primitives)) {
        const categoryNames = (primitivesByCategory[category] ??= []);
        for (const [path, value] of flattenScalars(tree)) {
            const name = cssVariableName([category], path);
            names.push(name);
            categoryNames.push(name);
            lines.push(`    ${name}: ${value};`);
        }
    }
    // `["semantic", category]`, not `[category]` — found live: a flat category's
    // primitive tier and semantic tier can name a leaf identically (radius's
    // primitive `pill` step and its semantic `pill` ROLE both flattened to
    // "radius-pill", tripping DS007 the first time this ran for real). Color's
    // theme roles don't need the same prefix (empirically confirmed by the same
    // DS007 check never firing for color, and it would deviate from
    // ARCHITECTURE.md's already-accepted `--ds-color-*` contract for no reason).
    const registry = { ...input.primitives, semantic: input.flatSemantics };
    for (const [category, tree] of Object.entries(input.flatSemantics)) {
        const resolved = resolveTree(tree, registry);
        for (const [path, value] of flattenScalars(resolved)) {
            const name = cssVariableName(["semantic", category], path);
            names.push(name);
            lines.push(`    ${name}: ${value};`);
        }
    }
    return { lines, primitiveNames: names, primitivesByCategory };
}

/**
 * `    --ds-color-accent: hsl(...);` -> `--ds-color-accent`. Cuts at the first
 * colon rather than splitting on every one of them: a declaration's value can
 * itself contain colons, and only the first is the separator.
 */
function declaredVariableName(line: string): string {
    const trimmed = line.trim();
    const separator = trimmed.indexOf(":");
    return separator === -1 ? trimmed : trimmed.slice(0, separator);
}

export function compileDesignTokens(input: CompilerInput): CompileResult {
    const { warnings } = validateDesignTokens(input);

    const flat = printFlatDeclarations(input);
    const resolved: Record<string, ResolvedThemeData> = {};
    const themeBlocks: string[] = [];
    const themeNames = Object.keys(input.themes);
    // Every declaration the first theme emitted, so the override blocks can
    // leave out the ones they would only repeat. A composite or component that
    // references no theme role — a text style, a transition — resolves
    // identically for every theme, and re-stating it under `.theme-dark` sets
    // the variable to the value it already had. Harmless, and pure noise: it
    // buried the handful of declarations that DO change in a wall of ones that
    // do not, which is exactly the diff someone reads to check a theme.
    const baseDeclarations = new Set<string>();

    themeNames.forEach((themeName, index) => {
        const registry = buildRegistry(input, themeName);
        const colorTree = input.themes[themeName]?.color ?? {};
        const resolvedColor = resolveTree(colorTree, registry);
        const colorLines = flattenScalars(resolvedColor).map(([path, value]) => `    ${cssVariableName(["color"], path)}: ${value};`);
        const { lines: compositeLines, byKind: compositeData } = serializeCompositesFor(input.composites, registry);
        const { lines: componentLines, data: componentData } = serializeComponentsFor(input.components, registry);

        resolved[themeName] = {
            color: resolvedColor,
            component: componentData,
            composite: compositeData,
        };

        validateUniqueVariableNames([
            ...flat.primitiveNames,
            ...colorLines.map(declaredVariableName),
            ...compositeLines.map(declaredVariableName),
            ...componentLines.map(declaredVariableName),
        ]);

        // `color-scheme` is a real CSS property, not a `--ds-*` custom one, and the browser only
        // understands the literal values "light"/"dark" — emitted only when a theme is actually
        // named one of those (true for this project's dark/light) rather than guessed from index
        // order, so an exotic multi-theme project just doesn't get a (possibly wrong) declaration.
        const colorScheme = themeName === "dark" || themeName === "light" ? `\n    color-scheme: ${themeName};` : "";
        const allDeclarations = [...colorLines, ...compositeLines, ...componentLines];
        const isBase = index === 0;
        if (isBase) for (const line of allDeclarations) baseDeclarations.add(line);

        const declarationLines = isBase ? allDeclarations : allDeclarations.filter((line) => !baseDeclarations.has(line));
        const selector = isBase ? ":root" : `.theme-${themeName}`;
        const body = isBase ? [...flat.lines, ...declarationLines].join("\n") : declarationLines.join("\n");
        themeBlocks.push(`${selector} {\n${body}${colorScheme}\n}`);
    });

    // No project path here, deliberately: this package is consumed by more than one
    // project (see README.md), each with its own layout, and a hardcoded path would be
    // wrong for whichever one didn't write it — exactly the assumption this engine's own
    // "ships zero color/role names of its own" promise exists to rule out for vocabulary.
    // A consuming project's own generate script is free to add a project-specific header
    // of its own around this data.
    const header = "/*\n * AUTO-GENERATED FILE. DO NOT EDIT MANUALLY.\n"
        + " * Source: the token definition modules passed to compileDesignTokens().\n"
        + " * Generator: the project's own token-generation script.\n */";

    return { css: [header, ...themeBlocks].join("\n\n"), resolved, warnings, primitiveVariables: flat.primitivesByCategory };
}
