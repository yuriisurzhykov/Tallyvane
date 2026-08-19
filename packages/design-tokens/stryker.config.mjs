// Mutation testing scope for packages/design-tokens/ — see ARCHITECTURE.md
// section 18.3 for the discipline this follows. This package's whole job is
// catching OTHER code's mistakes (DS0xx-DS2xx), so it needs its own
// correctness proof, not just coverage.
//
// Unlike backend/frontend, this package needs NO dedicated
// vitest.mutation.config.ts: every test file here is a plain, DB-free,
// jsdom-free Node unit test (no Postgres, no React rendering) — there is
// nothing problematic for Stryker's dry run to accidentally discover, so
// pointing it straight at the normal vitest.config.ts is safe. Verified by
// actually running `npm run test:mutation`, not assumed from the other
// two packages' pattern.
//
// `types.ts` and `index.ts` are deliberately NOT in `mutate` — the former
// is type-only declarations (nothing for Stryker to mutate at runtime),
// the latter a barrel re-export with no logic of its own.
/** @type {import('@stryker-mutator/api/core').PartialStrykerOptions} */
const config = {
    testRunner: "vitest",
    mutate: [
        "src/authoring.ts",
        "src/merge.ts",
        "src/references.ts",
        "src/validate.ts",
        "src/usage-graph.ts",
        "src/compile.ts",
        "src/serializers/css-value.ts",
        "src/serializers/gradient.ts",
        "src/serializers/shadow.ts",
        "src/eslint/no-raw-color-value.ts",
        "src/eslint/no-arbitrary-color-class.ts",
        "src/eslint/no-raw-dimension-value.ts",
        "src/eslint/no-arbitrary-dimension-class.ts",
        "src/eslint/ast-helpers.ts",
    ],
    reporters: ["html", "clear-text", "progress"],
    ignoreStatic: true,
    thresholds: {
        high: 90,
        low: 80,
        break: 85,
    },
};

export default config;
