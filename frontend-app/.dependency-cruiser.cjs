/**
 * File-level dependency rules. Identical intent to `frontend-web/.dependency-cruiser.cjs`
 * and `frontend-admin/.dependency-cruiser.cjs` — see their header comments — scoped to
 * this app's own `app/` and `src/`.
 */
module.exports = {
    forbidden: [
        {
            name: "no-circular",
            severity: "error",
            comment:
                "A cycle means neither module can be understood, tested or replaced without the other, and " +
                "the pair will keep growing because nothing stops it. The type system does not object: " +
                "TypeScript resolves cycles happily and only fails at runtime, sometimes only in production, " +
                "when one side reads a binding the other has not initialised yet.",
            from: {},
            to: { circular: true },
        },
        {
            name: "no-orphans",
            severity: "error",
            comment:
                "A module nothing imports is either dead or about to be. Configuration files and type " +
                "declarations are exempt because they are read by tooling rather than imported.",
            from: {
                orphan: true,
                pathNot: [
                    "(^|/)\\.[^/]+\\.(js|cjs|mjs|ts|json)$",
                    "\\.d\\.ts$",
                    "(^|/)tsconfig\\.json$",
                    "(^|/)steiger\\.config\\.ts$",
                    // The composition root and its routes: Next imports these,
                    // by file position rather than by an import statement, so
                    // the graph cannot see who reaches them.
                    "^app/",
                    // Reached through `scripts/generate-design-tokens.ts`, which
                    // is itself an entry point rather than an imported module.
                    "^scripts/",
                ],
            },
            to: {},
        },
        {
            name: "no-deprecated-core",
            severity: "error",
            comment: "Node core modules that have been deprecated for years and have direct replacements.",
            from: {},
            to: { dependencyTypes: ["core"], path: ["^(punycode|domain|sys|constants)$"] },
        },
        {
            name: "not-to-dev-dep",
            severity: "error",
            comment:
                "Shipping code must not import a development dependency. The build succeeds locally, where " +
                "every dependency is present, and fails in production, where they are not.",
            from: { path: "^(app|src)/", pathNot: "\\.(spec|test)\\.(ts|tsx)$" },
            to: { dependencyTypes: ["npm-dev"] },
        },
        {
            // Mirrors the sibling apps' rule of the same name: the theme source
            // (and the token engine it alone may import) lives in
            // `packages/frontend-shared`, not in this app.
            name: "no-direct-token-engine-import",
            severity: "error",
            comment:
                "The token compiler is a build-time dependency of frontend-shared's own generation script, " +
                "never of this app's runtime code. Import `frontend-shared/ui/theme` instead, which exposes " +
                "only the already-resolved data.",
            from: { path: "^(app|src)/" },
            to: { path: "^\\.\\./\\.\\./packages/design-tokens/" },
        },
    ],

    options: {
        doNotFollow: { path: "node_modules" },
        tsPreCompilationDeps: true,
        tsConfig: { fileName: "tsconfig.json" },
        enhancedResolveOptions: {
            exportsFields: ["exports"],
            conditionNames: ["import", "require", "node", "default", "types"],
            extensions: [".ts", ".tsx", ".js", ".jsx"],
        },
        reporterOptions: {
            dot: { collapsePattern: "node_modules/(?:@[^/]+/[^/]+|[^/]+)" },
        },
    },
};
