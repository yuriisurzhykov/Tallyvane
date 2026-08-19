import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { pagesManifest } from "./pages.manifest";
import { seedTheme, THEMES } from "./utils/theme";

/**
 * Contrast measured by the WCAG 2.2 AA ratio — the compliance bar.
 *
 * This is the one that is actually enforceable. Every accessibility law in
 * force points at WCAG 2.x: the ADA, Section 508, the European Accessibility
 * Act. WCAG 3 is a working draft with no contrast algorithm chosen and no legal
 * standing anywhere, so passing `contrast-apca.spec.ts` is evidence of nothing
 * to an auditor.
 *
 * The scenario this guards against is concrete rather than theoretical: someone
 * runs an off-the-shelf checker against the product and files a complaint.
 * Those checkers implement this ratio. "We measure with a better model" is not
 * an argument that survives contact with a legal letter.
 *
 * Kept as its own file, separate from both the structural scan and the APCA
 * one, so that each answers exactly one question. A merged result would say
 * "contrast failed" without saying under which model — and the two disagree in
 * both directions, so that distinction is the whole point.
 *
 * Only `color-contrast` runs here. `color-contrast-enhanced` is the AAA
 * threshold, which no law requires and which would reject colour pairs that are
 * perfectly readable; adding it would turn the compliance floor into an opinion.
 */
const WCAG_AA_CONTRAST = "color-contrast";

for (const entry of pagesManifest) {
    for (const theme of THEMES) {
        test(`${entry.name} @ ${theme} — contrast (WCAG 2.2 AA)`, async ({ page }, testInfo) => {
            await seedTheme(page, theme);
            await page.goto(entry.path);
            await page.waitForLoadState("networkidle");

            const results = await new AxeBuilder({ page }).withRules([WCAG_AA_CONTRAST]).analyze();

            // Its own attachment name, not the structural suite's `axe-results`.
            // Both are axe violations, but the run summary reports them as
            // separate lines — a broken landmark and an unreadable colour are
            // different problems, and a single total hides which you have.
            await testInfo.attach("wcag-contrast-results", {
                body: JSON.stringify(results.violations, null, 2),
                contentType: "application/json",
            });

            expect(results.violations, formatViolations(results.violations)).toEqual([]);
        });
    }
}

type AxeViolation = Awaited<ReturnType<AxeBuilder["analyze"]>>["violations"][number];

/**
 * Every offending element is listed, not just the rule. A contrast violation
 * reported as one line names a rule everyone already knows and leaves the
 * search for the actual elements to the reader.
 */
function formatViolations(violations: readonly AxeViolation[]): string {
    if (violations.length === 0) return "";
    const lines = violations.flatMap((violation) =>
        violation.nodes.map((node) => `- ${node.target.join(" ")}\n    ${node.failureSummary ?? violation.help}`),
    );
    return `WCAG 2.2 AA contrast violations:\n${lines.join("\n")}`;
}
