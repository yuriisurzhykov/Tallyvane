import { defineConfig } from "steiger";
import fsd from "@feature-sliced/steiger-plugin";

/**
 * Identical rationale to `frontend-web/steiger.config.ts`. Unlike
 * `frontend-admin`, this app does have a local `entities` layer (`job`,
 * `company`, `application`, … — ARCHITECTURE.md §12.5) once it's built; both
 * rules below are off only because the app is still a skeleton, and should
 * come back the moment there is anything to check.
 */
export default defineConfig([
    ...fsd.configs.recommended,
    {
        ignores: ["**/*.md"],
    },
    {
        files: ["./src/**"],
        rules: {
            "fsd/insignificant-slice": "off",
            "fsd/no-public-api-sidestep": "off",
        },
    },
]);
