import { defineConfig } from "steiger";
import fsd from "@feature-sliced/steiger-plugin";

/**
 * Identical rationale to `frontend-web/steiger.config.ts`. This app has no local
 * `entities` layer at all — `content-page`/`media-asset` live in `content-kit`
 * — which is expected, not a gap: nothing here is domain-entity shaped that
 * `frontend` doesn't already also need.
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
