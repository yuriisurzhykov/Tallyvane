import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { seedTheme, THEMES, withThemeGlobal } from "../utils/theme";
import type { PageEntry } from "../types";

/**
 * Contrast measured by the WCAG 2.2 AA ratio — the compliance bar.
 *
 * This is the one that is actually enforceable. Every accessibility law in
 * force points at WCAG 2.x: the ADA, Section 508, the European Accessibility
 * Act. WCAG 3 is a working draft with no contrast algorithm chosen and no legal
 * standing anywhere, so passing the APCA spec is evidence of nothing to an
 * auditor.
 *
 * The scenario this guards against is concrete rather than theoretical: someone
 * runs an off-the-shelf checker against the product and files a complaint.
 * Those checkers implement this ratio. "We measure with a better model" is not
 * an argument that survives contact with a legal letter.
 *
 * Kept as its own function, separate from both the structural scan and the
 * APCA one, so that each answers exactly one question. A merged result would
 * say "contrast failed" without saying under which model — and the two
 * disagree in both directions, so that distinction is the whole point. Either
 * can be deleted (`defineApcaContrastSpecs`, `utils/apca.ts`) without touching
 * this one.
 *
 * Only `color-contrast` runs here. `color-contrast-enhanced` is the AAA
 * threshold, which no law requires and which would reject colour pairs that are
 * perfectly readable; adding it would turn the compliance floor into an opinion.
 */
const WCAG_AA_CONTRAST = "color-contrast";

export function defineWcagContrastSpecs(manifest: readonly PageEntry[]): void {
    for (const entry of manifest) {
        for (const theme of THEMES) {
            test(`${entry.name} @ ${theme} — contrast (WCAG 2.2 AA)`, async ({ page }, testInfo) => {
                await seedTheme(page, theme);
                await page.goto(withThemeGlobal(entry.path, theme));
                await page.waitForLoadState("networkidle");

                const results = await new AxeBuilder({ page }).withRules([WCAG_AA_CONTRAST]).analyze();

                // Its own attachment name, not the structural suite's `axe-results`.
                // Both are axe violations, but the run summary reports them as
                // separate lines — a broken landmark and an unreadable colour are
                // different problems, and a single total hides which you have.
                await testInfo.attach("wcag-contrast-results", {
                    body: JSON.stringify({ violations: results.violations, incomplete: results.incomplete }, null, 2),
                    contentType: "application/json",
                });

                /**
                 * Unlike the structural suite, an incomplete result fails here.
                 *
                 * axe reports `incomplete` when it cannot compute a contrast at
                 * all — text over an image or a gradient, an ancestor with opacity,
                 * an element it judges obscured. Treating those as passes is the
                 * quiet failure mode of every contrast check: the elements hardest
                 * to measure are exactly the ones most likely to be wrong, and a
                 * suite that skips them reports green over unread text.
                 */
                expect(
                    [...results.violations, ...results.incomplete],
                    formatViolations(results.violations, results.incomplete),
                ).toEqual([]);
            });
        }
    }
}

type AxeViolation = Awaited<ReturnType<AxeBuilder["analyze"]>>["violations"][number];

/**
 * Every offending element is listed, not just the rule. A contrast violation
 * reported as one line names a rule everyone already knows and leaves the
 * search for the actual elements to the reader.
 */
function formatViolations(violations: readonly AxeViolation[], incomplete: readonly AxeViolation[]): string {
    const sections: string[] = [];

    if (violations.length > 0) {
        sections.push(`WCAG 2.2 AA contrast violations:\n${describe(violations)}`);
    }
    if (incomplete.length > 0) {
        sections.push(
            "Contrast could not be determined for these — treated as failures, since an unmeasured " +
            `pair is an unknown one rather than a passing one:\n${describe(incomplete)}`,
        );
    }
    return sections.join("\n\n");
}

function describe(results: readonly AxeViolation[]): string {
    return results
        .flatMap((result) => result.nodes.map((node) => `- ${node.target.join(" ")}\n    ${node.failureSummary ?? result.help}`))
        .join("\n");
}
