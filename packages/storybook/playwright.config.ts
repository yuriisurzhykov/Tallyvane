import { defineConfig } from "@playwright/test";
import {
    createReporters,
    SCREENSHOT_EXPECT_OPTIONS,
    STANDARD_RUN_OPTIONS,
    STANDARD_USE_OPTIONS,
    VIEWPORT_PROJECTS,
} from "test-kit/playwright/shared-config";

/** Distinct from frontend-web's 3100 and Storybook's own dev-server default of 6006, so none of the three ever collide on one machine. */
const PORT = 6007;
const LOCAL_BASE_URL = `http://localhost:${ String(PORT) }`;

const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? LOCAL_BASE_URL;

export default defineConfig({
    testDir: "./tests/e2e",
    timeout: 60_000,
    snapshotDir: "./tests/visual-snapshots",
    snapshotPathTemplate: "{snapshotDir}/{arg}-{projectName}{ext}",

    ...STANDARD_RUN_OPTIONS,
    reporter: createReporters(),

    use: {
        baseURL,
        ...STANDARD_USE_OPTIONS,
    },

    expect: SCREENSHOT_EXPECT_OPTIONS,

    projects: VIEWPORT_PROJECTS,

    /**
     * Serves the already-built `storybook-static/`; it does not build it.
     * `story-manifest.ts` reads `storybook-static/index.json` synchronously
     * while Playwright is still loading `.spec.ts` files — before this
     * `webServer` even starts — so the build has to be a separate, earlier
     * step. That step lives in `package.json`'s `test:*` scripts
     * (`pnpm run build-storybook && playwright test ...`), not here.
     */
    ...(process.env.PLAYWRIGHT_BASE_URL
        ? {}
        : {
            webServer: {
                command: `pnpm exec http-server storybook-static -p ${ String(PORT) } -s`,
                // A real static file that only exists once the build actually
                // ran, not the origin — the same reasoning frontend-web's own
                // config already applies to `/storybook`.
                url: `${ LOCAL_BASE_URL }/index.json`,
                reuseExistingServer: !process.env.CI,
                timeout: 60_000,
            },
        }),
});
