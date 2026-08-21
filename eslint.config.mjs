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
            // Storybook's own build output — the same shape of problem as
            // playwright-report above: a minified bundle Storybook ships, not
            // source anyone here edits. Building it locally before running
            // root lint produced thousands of findings against code nobody
            // can change here either.
            "**/storybook-static/**",
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
    //
    // `strictTypeChecked` supersedes `recommendedTypeChecked` (it already
    // contains `recommended` + `recommended-type-checked` + `strict`, per
    // typescript-eslint's own docs), and `stylisticTypeChecked` likewise
    // already contains the non-type-checked `stylistic` config. Neither adds
    // a duplicate of the other. Together they are this workspace's mechanical
    // proxy for the parts of SOLID a linter can actually see: `no-deprecated`,
    // `no-unnecessary-condition` and `no-non-null-assertion` close the escape
    // hatches that would otherwise let an unsound override slip past as `any`
    // (Liskov substitution is enforced structurally by `tsc` itself; these
    // rules stop `any`/`!` from quietly opting back out of that), and
    // `consistent-type-definitions` keeps object contracts as `interface`.
    ...tseslint.configs.strictTypeChecked.map((config) => ({ ...config, files: TS_FILES })),
    ...tseslint.configs.stylisticTypeChecked.map((config) => ({ ...config, files: TS_FILES })),

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

            /**
             * Open/Closed: a switch over a discriminated union or enum is
             * exactly the "closed for modification" seam OCP describes — a
             * new member added anywhere else is caught here rather than
             * silently falling through. Not paired with core `default-case`:
             * a mandatory `default` clause would silently swallow the exact
             * new-member case this rule exists to surface, defeating it.
             */
            "@typescript-eslint/switch-exhaustiveness-check": ["error", { considerDefaultExhaustiveForUnions: true }],
            /**
             * Interface Segregation, one level down from a type: a class
             * that doesn't say which of its members are its actual contract
             * leaks its whole shape as if all of it were public.
             */
            "@typescript-eslint/explicit-member-accessibility": "error",
        },
    },

    /**
     * SRP and ISP proxies. Neither rule below needs type information, so
     * they run on plain JS (`**\/*.{js,mjs,cjs}`) as well as TS — a CI
     * script or a config file can still grow into a god function. A class or
     * function that has crossed one of these thresholds is not automatically
     * wrong, but it is exactly the shape a responsibility creeping in looks
     * like, and a parameter list past four is usually asking for a narrower
     * interface rather than a fatter one.
     */
    {
        files: [...TS_FILES, "**/*.{js,mjs,cjs}"],
        rules: {
            complexity: ["error", 15],
            "max-depth": ["error", 4],
            "max-params": ["error", 4],
            "max-lines-per-function": ["error", { max: 80, skipBlankLines: true, skipComments: true, IIFEs: true }],
            "max-lines": ["error", { max: 300, skipBlankLines: true, skipComments: true }],
            "max-classes-per-file": ["error", 1],
        },
    },

    /**
     * `max-lines-per-function`, `max-lines` and `max-classes-per-file` all
     * measure the shape of a *unit of responsibility* — which is exactly the
     * wrong lens for a test file. A `describe` block is one JS function
     * wrapping as many `it` cases as the suite needs, and a mock class
     * declared inline (a fake `Image`, a fake reporter double) is test
     * fixture, not a second responsibility living in the same module. Both
     * are legitimate shapes for a test to take, not the SRP/ISP smell these
     * three rules exist to catch in real code, so test/story files are
     * exempted from exactly these three — not from the rest of the proxy
     * set, which still applies (a test *function* that's needlessly
     * complex, or a test file that repeats a giant inline class string, is
     * still worth flagging).
     */
    {
        files: ["**/*.test.{ts,tsx}", "**/*.spec.{ts,tsx}", "**/*.stories.tsx"],
        rules: {
            "max-lines-per-function": "off",
            "max-lines": "off",
            "max-classes-per-file": "off",
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
        files: [
            "frontend-web/**/*.tsx",
            "frontend-admin/**/*.tsx",
            "packages/frontend-shared/**/*.tsx",
            // content-kit is empty scaffolding today, but every public
            // content block it will hold renders real markup — the same
            // token discipline has to hold there once it does.
            "packages/content-kit/**/*.tsx",
        ],
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
            "design-tokens/no-raw-icon-size": "error",
        },
    },

    /**
     * Component-reuse governance: the other half of "shared/ui or nothing".
     * The design-token block above stops a literal colour or size from
     * reaching a class name; this block stops the element that class name
     * would sit on from being written raw in the first place. JSX itself
     * already marks the distinction this rule reads — an intrinsic element
     * (`button`, `div`) starts lowercase, a component (`Button`) does not —
     * so one selector catches every raw tag without enumerating them, and
     * cannot go stale as new primitives are added.
     *
     * Scoped to each app's `src/` rather than its whole tree on purpose:
     * Next's routing directory (`frontend-web/app/**`) is a sibling of
     * `src/`, not inside it, and the root `layout.tsx` there *must* render
     * raw `<html>`/`<body>`, exactly as `opengraph-image.tsx` *must* build
     * raw JSX for `ImageResponse`. That is the same "routes vs FSD app
     * layer" split `appBoundariesBlock` already draws, reused here instead
     * of re-deriving it as an `ignores` pattern.
     */
    {
        files: [
            "frontend-web/src/**/*.tsx",
            "frontend-admin/src/**/*.tsx",
            "packages/frontend-shared/src/shared/**/*.tsx",
            "packages/content-kit/src/**/*.tsx",
        ],
        ignores: [
            // Where the primitives are actually implemented — the one place
            // a raw element is the content, not a violation of the rule.
            "packages/frontend-shared/src/shared/ui/**",
        ],
        rules: {
            "no-restricted-syntax": ["error", {
                selector: "JSXOpeningElement[name.type='JSXIdentifier'][name.name=/^[a-z]/]",
                message:
                    "Raw JSX elements are banned outside packages/frontend-shared/src/shared/ui — use the matching shared/ui primitive, or add one there if it doesn't exist yet (see COMPONENTS.md).",
            }],
        },
    },

    /**
     * Vendor-primitive governance: the Dependency Inversion half of the same
     * rule. `frontend-shared` is the one package that depends on Base UI,
     * TanStack Table/Virtual and Lucide directly (per its own package.json
     * comment); everywhere else is meant to depend on its wrapper instead of
     * the concrete library. `frontend-shared` is simply absent from this
     * block's `files`, so no `ignores` entry is needed to carve it back out.
     */
    {
        files: [
            "frontend-web/src/**/*.{ts,tsx}",
            "frontend-admin/src/**/*.{ts,tsx}",
            "packages/content-kit/src/**/*.{ts,tsx}",
        ],
        rules: {
            "no-restricted-imports": ["error", {
                paths: [
                    {
                        name: "@base-ui/react",
                        message: "Import Base UI primitives only from packages/frontend-shared — every primitive is wrapped there exactly once (ADR-031).",
                    },
                    {
                        name: "@tanstack/react-table",
                        message: "Use frontend-shared's DataTable instead of importing TanStack Table directly.",
                    },
                    {
                        name: "@tanstack/react-virtual",
                        message: "Use frontend-shared's DataTable instead of importing TanStack Virtual directly.",
                    },
                    {
                        name: "lucide-react",
                        message: "Route icons through frontend-shared's Icon component, not a direct lucide-react import.",
                    },
                ],
                patterns: [
                    {
                        group: ["@base-ui/react/*"],
                        message: "Import Base UI primitives only from packages/frontend-shared.",
                    },
                ],
            }],
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
