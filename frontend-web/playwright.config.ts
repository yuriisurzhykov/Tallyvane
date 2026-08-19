import { defineConfig } from "@playwright/test";
import {
    createReporters,
    SCREENSHOT_EXPECT_OPTIONS,
    STANDARD_RUN_OPTIONS,
    STANDARD_USE_OPTIONS,
    VIEWPORT_PROJECTS,
} from "test-kit/playwright/shared-config";

/**
 * 3100 rather than Next's default, so a suite run never collides with a dev
 * server someone left running on the same machine.
 */
const PORT = 3100;
const LOCAL_BASE_URL = `http://localhost:${ PORT }`;

/** Escape hatch for pointing the suite at an already-deployed URL instead of building locally. Unset by default. */
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? LOCAL_BASE_URL;

export default defineConfig({
    testDir: "./tests/e2e",
    timeout: 60_000,

    /**
     * One folder per page, named `<theme>-<viewport>.png` inside it. The OS is
     * deliberately absent from the name: baselines are produced on Linux, and a
     * platform suffix would let a local Windows run quietly create a second,
     * divergent set of "baselines" instead of failing and saying so.
     */
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
     * Spread rather than assigned, because `exactOptionalPropertyTypes` draws a
     * distinction the shorthand ternary erases: an absent property and one
     * explicitly set to `undefined` are different things, and only the first is
     * "let Playwright decide".
     *
     * The server is skipped entirely when an external base URL is given, so the
     * suite can be pointed at a deployment without building anything.
     */
    ...(process.env.PLAYWRIGHT_BASE_URL
        ? {}
        : {
            webServer: {
                command: `pnpm run build && pnpm exec next start -p ${ PORT }`,
                // A route that exists, not the origin. There is no page at `/`
                // yet, and Playwright reads the 404 as "not up" and waits out
                // the whole timeout — a three-minute hang whose message says
                // nothing about the missing route.
                url: `${ LOCAL_BASE_URL }/storybook`,
                reuseExistingServer: !process.env.CI,
                timeout: 180_000,
            },
        }),
});
