import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { pagesManifest } from "./pages.manifest";
import { seedTheme, THEMES } from "./utils/theme";

type AxeViolation = Awaited<ReturnType<AxeBuilder["analyze"]>>["violations"][number];

/**
 * Structural accessibility: roles, names, landmarks, heading order, form
 * labelling — everything a machine can decide without a human looking.
 *
 * Contrast is excluded here and has two files of its own,
 * `contrast-wcag.spec.ts` and `contrast-apca.spec.ts`. Three separate suites
 * rather than one, so each failure names the question it answers: a broken
 * landmark and an unreadable colour pair are different problems with different
 * owners, and a colour pair measured two ways is two answers rather than one.
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
