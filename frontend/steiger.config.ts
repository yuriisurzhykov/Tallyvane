import { defineConfig } from "steiger";
import fsd from "@feature-sliced/steiger-plugin";

/**
 * Feature-Sliced Design's own canonical rules, which overlap with
 * `eslint-plugin-boundaries` only at the edges. Boundaries knows the import
 * matrix; Steiger knows the methodology — that a slice has a public API, that
 * segment names come from a fixed set, that a slice holding one file is
 * probably not a slice at all.
 *
 * Two rules are off while the application is still a skeleton, and both should
 * come back the moment there is anything to check. `insignificant-slice` and
 * `no-public-api-sidestep` both need real slices with real consumers to say
 * anything true; against empty directories they only report that the project
 * has not been written yet.
 */
export default defineConfig([
    ...fsd.configs.recommended,
    {
        ignores: ["**/*.md", "src/shared/ui/theme/generated/**"],
    },
    {
        files: ["./src/**"],
        rules: {
            "fsd/insignificant-slice": "off",
            "fsd/no-public-api-sidestep": "off",
        },
    },
]);
