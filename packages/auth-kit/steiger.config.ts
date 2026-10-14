import { defineConfig } from "steiger";
import fsd from "@feature-sliced/steiger-plugin";

/**
 * Three FSD layers (`entities`, `features`, `views`) and a `shared` layer of the
 * package's own, because the sign-in flow reaches from the viewer and the state
 * of an attempt up to the pages a route renders. `frontend-shared` is an external
 * package dependency here, as it is in `content-kit`.
 *
 * The two rules below are off for the reason they are off in the apps: the flow
 * moved here whole, and a slice that only one page uses is still the unit it was.
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
