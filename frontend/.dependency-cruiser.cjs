/**
 * File-level dependency rules. Where `eslint-plugin-boundaries` reasons about
 * layers and Steiger about the methodology, this one reasons about the graph
 * itself — cycles that span more files than any single lint rule sees, and
 * modules nothing reaches.
 *
 * It also produces the graph as a build artefact, which is the cheapest way to
 * make architectural drift visible: a picture of the dependencies is read, a
 * list of rules is not.
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
                    "(^|/)(steiger|compiler)\\.config\\.ts$",
                    // The composition root and its routes: Next imports these,
                    // by file position rather than by an import statement, so
                    // the graph cannot see who reaches them.
                    "^app/",
                    // Reached through `scripts/generate-design-tokens.ts`, which
                    // is itself an entry point rather than an imported module.
                    "^scripts/",
                    // Segment public APIs that are still empty. They exist so
                    // the boundary is declared before there is anything behind
                    // it; treating them as dead code inverts the intent.
                    "^src/shared/[^/]+/index\\.ts$",
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
            name: "engine-is-build-time-only",
            severity: "error",
            comment:
                "The token engine is reachable from the token source and the build script, and nowhere else. " +
                "Anywhere else it drags reference resolution, cycle detection and the whole validation " +
                "machinery into a bundle that has no use for any of it — by then the values are resolved. " +
                "Runtime consumers read `shared/ui/theme`, whose public API exposes the resolved data alone. " +
                "Note the path being matched: pnpm links a workspace package rather than copying it, so the " +
                "engine resolves to `../packages/design-tokens/...` and never to anything under node_modules. " +
                "A rule written against the node_modules path would match nothing and pass forever.",
            from: {
                path: "^(app|src)/",
                pathNot: "^src/shared/ui/theme/(tokens|contracts|themes|semantic|components|composites)/|^src/shared/ui/theme/compiler\\.config\\.ts$",
            },
            to: { path: "^\\.\\./packages/design-tokens/" },
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
