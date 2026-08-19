import { defineConfig } from "steiger";
import fsd from "@feature-sliced/steiger-plugin";

/**
 * Two FSD layers only — `entities` and `widgets` — because that's the whole
 * of what ADR-013 requires both apps to share. `frontend-shared` is an
 * external package dependency here, not a local `shared` layer.
 *
 * `no-segmentless-slices` is off for the same reason the other two are: it
 * fires on a bare `index.ts` with no `ui`/`model` segment folder underneath
 * it — exactly the shape every stub slice here has before the `content`
 * module gives it real content.
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
            "fsd/no-segmentless-slices": "off",
        },
    },
]);
