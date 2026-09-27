module.exports = {
    forbidden: [
        {
            name: "no-circular",
            severity: "error",
            from: {},
            to: { circular: true },
        },
        {
            name: "no-orphans",
            severity: "error",
            from: {
                orphan: true,
                pathNot: [
                    "(^|/)\\.[^/]+\\.(js|cjs|mjs|ts|json)$",
                    "\\.d\\.ts$",
                    "(^|/)steiger\\.config\\.ts$",
                    "^src/(entities|features|widgets)/[^/]+/index\\.ts$",
                ],
            },
            to: {},
        },
        {
            name: "not-to-dev-dep",
            severity: "error",
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
        reporterOptions: { dot: { collapsePattern: "node_modules/(?:@[^/]+/[^/]+|[^/]+)" } },
    },
};
