import js from "@eslint/js";
import tseslint from "typescript-eslint";
import globals from "globals";
import boundaries from "eslint-plugin-boundaries";
import importX from "eslint-plugin-import-x";
import reactHooks from "eslint-plugin-react-hooks";
import next from "@next/eslint-plugin-next";
import { createTypeScriptImportResolver } from "eslint-import-resolver-typescript";
import designTokens from "design-token-engine/eslint-plugin";

/**
 * One configuration for the whole workspace, rather than one per package.
 *
 * The rules that matter most here are about relationships BETWEEN parts —
 * which layer may import which, what may reach a design primitive — and a
 * per-package config can only ever see one side of such a relationship. A
 * single file also makes the answer to "why is this allowed" findable in one
 * place instead of three.
 *
 * Deviation from the specification's §15.5 worth knowing about:
 * `eslint-plugin-import` is named there, but its peer range stops at ESLint 9
 * and this workspace runs 10. `eslint-plugin-import-x` is the maintained fork
 * with the same rule set, so the rules are the specified ones even though the
 * package name is not.
 */

/**
 * Lowest first, across the whole methodology. Not every app has every layer
 * locally any more (ADR-032): `shared` is the `frontend-shared` package, and
 * `frontend-admin` has no local `entities` — `content-page`/`media-asset`
 * live in `content-kit`. Each scope below lists the subset it actually has,
 * in order, and `layerPolicies` builds that scope's permission matrix from
 * exactly that list. This is deliberately NOT how the two apps are kept from
 * importing each other — that boundary is the pnpm workspace graph itself
 * (`frontend-admin` declares no dependency on `frontend-web`), which a lint
 * rule could not enforce as reliably as a missing package.json entry does.
 */
function layerPolicies(layers) {
    return layers.map((layer, index) => ({
        from: [{ element: { type: layer } }],
        allow: layers.slice(0, index).map((type) => ({ to: { element: { type } } })),
    }));
}

/** The composition root sees every layer in its own scope — that's what routing is for. */
function routesPolicy(layers) {
    return {
        from: [{ element: { type: "routes" } }],
        allow: layers.map((type) => ({ to: { element: { type } } })),
    };
}

const FRONTEND_LAYERS = ["entities", "features", "widgets", "views", "app"];
const ADMIN_LAYERS = ["features", "widgets", "views", "app"];

const TS_FILES = ["**/*.ts", "**/*.tsx", "**/*.mts"];

/**
 * The FSD/Next boundaries block is identical in shape for `frontend-web` and
 * `frontend-admin` — only the directory, the local layer list and the Next
 * `rootDir` differ. Written once and applied twice rather than copy-pasted,
 * so the two apps' rules cannot quietly drift apart the way two pasted blocks
 * eventually do.
 */
function appBoundariesBlock(appDir, layers) {
    return {
        files: [`${appDir}/**/*.{ts,tsx}`],
        languageOptions: {
            globals: { ...globals.browser },
        },
        plugins: {
            boundaries,
            "import-x": importX,
            "react-hooks": reactHooks,
            "@next/next": next,
        },
        settings: {
            "boundaries/elements": [
                // `<app>/app` is Next's routing directory, NOT the FSD app
                // layer — that one is `<app>/src/app`. The two share a name
                // and nothing else, which is worth stating because getting
                // them confused makes the matrix below look wrong.
                { type: "routes", pattern: `${appDir}/app/**`, partialMatch: false },
                { type: "app", pattern: `${appDir}/src/app/*`, partialMatch: false },
                { type: "views", pattern: `${appDir}/src/views/*`, partialMatch: false },
                { type: "widgets", pattern: `${appDir}/src/widgets/*`, partialMatch: false },
                { type: "features", pattern: `${appDir}/src/features/*`, partialMatch: false },
                ...(layers.includes("entities")
                    ? [{ type: "entities", pattern: `${appDir}/src/entities/*`, partialMatch: false }]
                    : []),
            ],
            "boundaries/include": [`${appDir}/**/*.{ts,tsx}`],
            "import-x/resolver-next": [
                createTypeScriptImportResolver({
                    project: ["frontend-web/tsconfig.json", "frontend-admin/tsconfig.json", "packages/*/tsconfig.json"],
                    // A workspace genuinely has several tsconfigs; the resolver
                    // suggests merging them behind project references, which
                    // would couple packages that are deliberately independent.
                    noWarnOnMultipleProjects: true,
                }),
            ],
            // Without this the Next plugin looks for a routing directory at the
            // workspace root and warns on every run; the app lives one level in.
            next: { rootDir: appDir },
        },
        rules: {
            ...reactHooks.configs.recommended.rules,
            ...next.configs.recommended.rules,

            "boundaries/dependencies": ["error", {
                default: "disallow",
                policies: [routesPolicy(layers), ...layerPolicies(layers)],
            }],
            /**
             * Reaching into a slice past its `index.ts` makes every internal
             * file part of the public surface by accident, and the slice can
             * then never be reorganised without breaking callers who were never
             * supposed to see it.
             *
             * The plugin prints a deprecation notice for this rule on every
             * run, and it is kept anyway. `boundaries/dependencies` is the
             * suggested replacement, but expressing "the import must land on
             * the slice's index" through its selectors is not obvious, and a
             * selector that is subtly wrong does not fail loudly — it matches
             * nothing and permits everything. A visible warning beats a rule
             * that has quietly stopped checking.
             */
            "boundaries/entry-point": ["error", {
                default: "disallow",
                policies: [{ target: [{ element: { type: layers } }], allow: "index.{ts,tsx}" }],
            }],

            "import-x/no-cycle": ["error", { maxDepth: Infinity }],
            "import-x/no-self-import": "error",
            "import-x/no-useless-path-segments": "error",
        },
    };
}

export default [
    {
        ignores: [
            "**/node_modules/**",
            "**/.next/**",
            "**/dist/**",
            // Next writes this and owns it; it is not in any tsconfig we
            // control, so the type-aware parser cannot read it either.
            "**/next-env.d.ts",
            // Playwright's own report is a bundled application it ships, not
            // source. Linting it produced over a thousand findings about code
            // nobody here can change.
            "**/playwright-report/**",
            "**/test-results/**",
            "**/tests/visual-snapshots/**",
            // Compiler output. Linting it would report on decisions made in the
            // token source, at coordinates that exist in neither file.
            "packages/frontend-shared/src/shared/ui/theme/generated/**",
        ],
    },

    js.configs.recommended,

    // Plain JavaScript — the config files and the CI helper scripts. They run
    // under Node and nothing else, and the type-aware rules below deliberately
    // skip them, so this is the only place they get their globals.
    {
        files: ["**/*.{js,mjs,cjs}"],
        languageOptions: { globals: { ...globals.node } },
    },

    // Scoped to TypeScript explicitly. Type-aware rules ask the parser for a
    // type checker, and applying them to a plain `.mjs` file — this config
    // among them — fails outright rather than degrading, because no tsconfig
    // covers it.
    ...tseslint.configs.recommendedTypeChecked.map((config) => ({ ...config, files: TS_FILES })),

    {
        files: TS_FILES,
        languageOptions: {
            parserOptions: {
                // Type-aware linting across the workspace. Slower than the
                // syntactic-only mode and worth it: the rules that catch real
                // defects — floating promises, unsafe `any` flowing into a
                // call — cannot be expressed without types.
                projectService: true,
                tsconfigRootDir: import.meta.dirname,
            },
            globals: { ...globals.node },
        },
        rules: {
            // A promise nobody waits for is the single most common way an
            // async bug becomes invisible: the operation fails, the rejection
            // goes nowhere, and the caller reports success.
            "@typescript-eslint/no-floating-promises": "error",
            "@typescript-eslint/no-misused-promises": "error",
            "@typescript-eslint/consistent-type-imports": ["error", { fixStyle: "inline-type-imports" }],
            "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_", varsIgnorePattern: "^_" }],
            eqeqeq: ["error", "always", { null: "ignore" }],
            "no-console": ["error", { allow: ["warn", "error"] }],
        },
    },

    // ---------------------------------------------------------------------
    // FRONTEND-WEB — blog + console
    // ---------------------------------------------------------------------
    appBoundariesBlock("frontend-web", FRONTEND_LAYERS),

    // ---------------------------------------------------------------------
    // FRONTEND-ADMIN — CMS admin, a separate app since ADR-032
    // ---------------------------------------------------------------------
    appBoundariesBlock("frontend-admin", ADMIN_LAYERS),

    // Design-token governance. Scoped to where markup lives, because these
    // rules are about literals written into JSX and inline styles.
    {
        files: ["frontend-web/**/*.tsx", "frontend-admin/**/*.tsx", "packages/frontend-shared/**/*.tsx"],
        ignores: [
            // The one place literal colours and dimensions ARE the content.
            "packages/frontend-shared/src/shared/ui/theme/**",
        ],
        plugins: { "design-tokens": designTokens },
        rules: {
            "design-tokens/no-raw-color-value": "error",
            "design-tokens/no-arbitrary-color-class": "error",
            "design-tokens/no-raw-dimension-value": "error",
            "design-tokens/no-arbitrary-dimension-class": "error",
            // The stacking order is the one thing the Tailwind theme cannot
            // protect: `z-50` is a bare-value utility built without consulting
            // it, so clearing the namespace changes nothing and the check has
            // to happen where the class is written.
            "design-tokens/no-unnamed-z-index-class": "error",
        },
    },

    // ---------------------------------------------------------------------
    // TOKEN ENGINE
    // ---------------------------------------------------------------------
    {
        files: ["packages/design-tokens/**/*.ts"],
        rules: {
            /**
             * ESLint hands rule visitors untyped AST nodes, and typing them
             * properly would mean depending on `@types/estree`'s node union at
             * every visitor — for no gain, since each visitor immediately
             * narrows by the property it reads. The looseness is confined to
             * this package's rule files and documented in its README.
             */
            "@typescript-eslint/no-explicit-any": "off",
            "@typescript-eslint/no-unsafe-member-access": "off",
            "@typescript-eslint/no-unsafe-assignment": "off",
            "@typescript-eslint/no-unsafe-argument": "off",
            "@typescript-eslint/no-unsafe-call": "off",
            "@typescript-eslint/no-unsafe-return": "off",
        },
    },

    // Build scripts talk to the operator; that is what their output is for.
    {
        files: ["**/scripts/**/*.ts", "**/*.config.{ts,mjs}", "eslint.config.mjs"],
        rules: { "no-console": "off" },
    },

    {
        files: ["frontend-web/steiger.config.ts", "frontend-admin/steiger.config.ts", "packages/*/steiger.config.ts"],
        rules: {
            /**
             * `@feature-sliced/steiger-plugin` does not ship usable types for
             * its preset, so spreading it reads as `any` and the unsafe-value
             * rules fire on a third-party gap rather than on anything written
             * here. Scoped to these files: switching the rules off more
             * broadly would hide the same class of defect in our own code,
             * which is the class they are best at catching.
             */
            "@typescript-eslint/no-unsafe-argument": "off",
            "@typescript-eslint/no-unsafe-assignment": "off",
        },
    },
];
