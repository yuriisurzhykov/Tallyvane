import { defineConfig } from "steiger";
import fsd from "@feature-sliced/steiger-plugin";

/**
 * This package holds exactly one FSD layer — `shared` — because that is all
 * that ever belonged here: see ARCHITECTURE.md's amendment to §12.5. The same
 * two rules `frontend-web/steiger.config.ts` disables are disabled here for the
 * same reason: the segments are declared before they have real content
 * (`api`, `i18n`, `lib`, `config`, `blocks` are still `export {}` stubs).
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
