import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { pagesManifest } from "./pages.manifest";
import { seedTheme, THEMES } from "./utils/theme";

type AxeViolation = Awaited<ReturnType<AxeBuilder["analyze"]>>["violations"][number];

/**
 * Structural accessibility: roles, names, landmarks, heading order, form
 * labelling — everything a machine can decide without a human looking.
 *
 * Colour contrast is deliberately excluded here and checked in
 * `contrast.spec.ts` instead. axe implements the WCAG 2 ratio, and running both
 * would produce two verdicts from two different models on the same pixels: one
 * pair passing here and failing there is not a contradiction to resolve but a
 * disagreement about which model to trust. This project trusts APCA, so this
 * suite is not asked the question.
 */
const CONTRAST_RULES = ["color-contrast", "color-contrast-enhanced"];

/** `critical` and `serious` fail the build. Lower impacts are still captured and attached, because they are worth reading without being worth blocking a merge. */
const BLOCKING_IMPACTS = new Set(["critical", "serious"]);

for (const entry of pagesManifest) {
    for (const theme of THEMES) {
        test(`${entry.name} @ ${theme} — a11y`, async ({ page }, testInfo) => {
            await seedTheme(page, theme);
            await page.goto(entry.path);
            await page.waitForLoadState("networkidle");

            const results = await new AxeBuilder({ page })
                .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "best-practice"])
                .disableRules(CONTRAST_RULES)
                .analyze();

            await testInfo.attach("axe-results", {
                body: JSON.stringify(results.violations, null, 2),
                contentType: "application/json",
            });

            const blocking = results.violations.filter((violation) => BLOCKING_IMPACTS.has(violation.impact ?? ""));
            expect(blocking, formatViolations(blocking)).toEqual([]);
        });
    }
}

function formatViolations(violations: readonly AxeViolation[]): string {
    if (violations.length === 0) return "";
    const lines = violations.map((violation) => {
        const where = violation.nodes.map((node) => node.target.join(" ")).join(", ");
        return `- [${violation.impact}] ${violation.id}: ${violation.help} (${where})`;
    });
    return `Accessibility violations found:\n${lines.join("\n")}`;
}
