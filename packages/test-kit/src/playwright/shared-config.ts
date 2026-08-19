import { devices, type Project, type ReporterDescription } from "@playwright/test";

/**
 * The three viewports every consumer's visual/a11y suite already agreed on.
 * `tablet` and `mobile` are scoped to `visual.spec.ts` by convention — a
 * consumer wiring these in keeps that `testMatch`, since structural a11y and
 * contrast do not need three renders of the same DOM.
 *
 * Not `devices["iPad (gen 7)"]` for `tablet`: Playwright's Apple-branded
 * presets select the WebKit engine, and these suites install Chromium only —
 * using one would fail with a missing executable rather than testing
 * anything. Taking Chromium and overriding the viewport gives the geometry
 * without the engine.
 */
export const VIEWPORT_PROJECTS: Project[] = [
    {
        name: "desktop",
        use: { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 900 } },
    },
    {
        name: "tablet",
        use: { ...devices["Desktop Chrome"], viewport: { width: 834, height: 1194 }, isMobile: true, hasTouch: true },
        testMatch: /visual\.spec\.ts/,
    },
    {
        name: "mobile",
        use: { ...devices["Desktop Chrome"], viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true },
        testMatch: /visual\.spec\.ts/,
    },
];

/** `fullyParallel`/`forbidOnly`/`retries` — the same CI-vs-local behaviour every consumer wants, computed once. */
export const STANDARD_RUN_OPTIONS = {
    fullyParallel: true,
    forbidOnly: !!process.env.CI,
    retries: process.env.CI ? 1 : 0,
} as const;

/**
 * `trace`/`screenshot` policy. `baseURL` stays out of this object and out of
 * this package entirely — it depends on a consumer's own `webServer` port,
 * which is the one piece of config genuinely specific to each of the three
 * places this runs.
 */
export const STANDARD_USE_OPTIONS = {
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
} as const;

/**
 * Playwright retries a screenshot until two consecutive captures agree or
 * this elapses. The default five seconds is tight on a cold, CPU-contended
 * runner, and the failure it produces looks like a visual regression rather
 * than the timing problem it is. `maxDiffPixelRatio` leaves room for
 * sub-pixel antialiasing noise — a real layout or colour regression is far
 * above it.
 */
export const SCREENSHOT_EXPECT_OPTIONS = {
    timeout: 15_000,
    toHaveScreenshot: {
        maxDiffPixelRatio: 0.02,
        animations: "disabled",
    },
} as const;

/**
 * The HTML report is for a person looking at a failed diff; the summary is
 * for a script deciding what to say about the run. Both always, because the
 * digest costs nothing and a local run benefits from it the same way CI does.
 */
export function createReporters(): ReporterDescription[] {
    return [
        ...(process.env.CI ? [["github"] as ReporterDescription] : [["list"] as ReporterDescription]),
        ["html", { open: "never" }] as ReporterDescription,
        ["test-kit/reporters/summary-reporter"] as ReporterDescription,
    ];
}
