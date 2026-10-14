/**
 * File-level dependency rules for the `auth-kit` package. Same intent as
 * `frontend-app/.dependency-cruiser.cjs`, scoped to a package holding the
 * `entities`, `features` and `views` FSD layers and a small `shared` segment.
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
                    "(^|/)steiger\\.config\\.ts$",
                    // The package's entry points: the apps reach them through the
                    // `exports` map of package.json, which no import statement
                    // inside the package mentions.
                    "^src/(entities|features|views)/[^/]+/index\\.ts$",
                    "^src/shared/(texts|return-path)/index\\.ts$",
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
