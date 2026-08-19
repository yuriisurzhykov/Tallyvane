import { defineConfig } from "vitest/config";

export default defineConfig({
    test: {
        environment: "jsdom",
        setupFiles: ["./tests/setup.ts"],
        // No component exists yet in this package — Tier 0 primitives land
        // batch by batch starting after this prerequisite work. Without this,
        // `vitest run` exits non-zero on an empty suite and `pnpm verify`
        // never turns green until the very first `*.test.tsx` is written.
        passWithNoTests: true,
    },
});
