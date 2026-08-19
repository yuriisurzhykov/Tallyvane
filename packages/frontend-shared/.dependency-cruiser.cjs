/**
 * File-level dependency rules for the `frontend-shared` package. Same intent
 * as `frontend-web/.dependency-cruiser.cjs`, scoped to a package that holds only
 * the `shared` FSD layer.
 */
module.exports = {
    forbidden: [
        {
            name: "no-circular",
            severity: "error",
            comment:
                "A cycle means neither module can be understood, tested or replaced without the other, and " +
                "the pair will keep growing because nothing stops it.",
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
                    "(^|/)(steiger|compiler)\\.config\\.ts$",
                    // Segment public APIs that are still empty. They exist so
                    // the boundary is declared before there is anything behind
                    // it; treating them as dead code inverts the intent.
                    "^src/shared/[^/]+/index\\.ts$",
                    // A test is an entry point Vitest finds by filename glob,
                    // not a module anything imports — it will always look like
                    // an orphan to a check that only follows `import`, the
                    // same reason config files and `.d.ts` are exempt above.
                    "\\.(spec|test)\\.(ts|tsx)$",
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
            from: { path: "^src/", pathNot: "\\.(spec|test)\\.(ts|tsx)$" },
            to: { dependencyTypes: ["npm-dev"] },
        },
        {
            name: "engine-is-build-time-only",
            severity: "error",
            comment:
                "The token engine is reachable from the token source, and nowhere else in this package. " +
                "Runtime consumers (this package's own exports, and both apps) read `shared/ui/theme`'s " +
                "resolved output instead. See frontend-web/.dependency-cruiser.cjs for the full reasoning — " +
                "identical here, just re-rooted at this package.",
            from: {
                path: "^src/",
                pathNot: "^src/shared/ui/theme/(tokens|contracts|themes|semantic|components|composites)/|^src/shared/ui/theme/compiler\\.config\\.ts$",
            },
            to: { path: "^\\.\\./design-tokens/" },
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
