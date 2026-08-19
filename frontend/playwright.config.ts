import { defineConfig, devices } from "@playwright/test";

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

    fullyParallel: true,
    forbidOnly: !!process.env.CI,
    retries: process.env.CI ? 1 : 0,

    /**
     * The HTML report is for a person looking at a failed diff; the summary is
     * for a script deciding what to say about the run. Both always, because the
     * digest costs nothing and a local run benefits from it the same way CI
     * does.
     */
    reporter: [
        ...(process.env.CI ? [["github"] as const] : [["list"] as const]),
        ["html", { open: "never" }] as const,
        ["./tests/e2e/reporters/summary-reporter.ts"] as const,
    ],

    use: {
        baseURL,
        trace: "retain-on-failure",
        screenshot: "only-on-failure",
    },

    expect: {
        // Playwright retries a screenshot until two consecutive captures agree
        // or this elapses. The default five seconds is tight on a cold, CPU
        // contended runner, and the failure it produces looks like a visual
        // regression rather than the timing problem it is.
        timeout: 15_000,
        toHaveScreenshot: {
            // Room for sub-pixel antialiasing noise. A real layout or colour
            // regression is far above this.
            maxDiffPixelRatio: 0.02,
            animations: "disabled",
        },
    },

    projects: [
        {
            name: "desktop",
            use: { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 900 } },
        },
        {
            /**
             * Not `devices["iPad (gen 7)"]`, deliberately. Playwright's
             * Apple-branded presets select the WebKit engine, and this suite
             * installs Chromium only — using one would fail with a missing
             * executable rather than testing anything. Taking Chromium and
             * overriding the viewport gives the geometry without the engine.
             */
            name: "tablet",
            use: {
                ...devices["Desktop Chrome"],
                viewport: { width: 834, height: 1194 },
                isMobile: true,
                hasTouch: true
            },
            testMatch: /visual\.spec\.ts/,
        },
        {
            name: "mobile",
            use: {
                ...devices["Desktop Chrome"],
                viewport: { width: 390, height: 844 },
                isMobile: true,
                hasTouch: true
            },
            testMatch: /visual\.spec\.ts/,
        },
    ],

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
