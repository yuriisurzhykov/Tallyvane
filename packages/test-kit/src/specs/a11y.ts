import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { seedTheme, THEMES, withThemeGlobal } from "../utils/theme";
import type { PageEntry } from "../types";

type AxeViolation = Awaited<ReturnType<AxeBuilder["analyze"]>>["violations"][number];

/**
 * Structural accessibility: roles, names, landmarks, heading order, form
 * labelling — everything a machine can decide without a human looking.
 *
 * Contrast is excluded here and has two specs of its own,
 * `defineWcagContrastSpecs` and `defineApcaContrastSpecs`. Three separate
 * checks rather than one, so each failure names the question it answers: a
 * broken landmark and an unreadable colour pair are different problems with
 * different owners, and a colour pair measured two ways is two answers rather
 * than one.
 */
const CONTRAST_RULES = ["color-contrast", "color-contrast-enhanced"];

/**
 * `wcag22a` and `wcag22aa` together, not `wcag22aa` alone. axe-core's WCAG
 * tags are not cumulative *within* a version either — 2.2 added Level A
 * criteria (3.2.6 Consistent Help, 3.3.7 Redundant Entry) as well as Level AA
 * ones, and `wcag22aa` only ever covered the AA half. Requesting only the AA
 * tag silently skips whatever rule axe has registered under the A one.
 *
 * `best-practice` is included even though no law requires it. Those rules
 * catch real problems earlier than the conformance ones, and the cost of
 * heeding them here is far lower than after there is a product.
 */
const TAGS = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22a", "wcag22aa", "best-practice"];

/** `critical` and `serious` fail the build. Lower impacts are still captured and attached, because they are worth reading without being worth blocking a merge. */
const BLOCKING_IMPACTS = new Set(["critical", "serious"]);

/**
 * Registers one `test()` per page/story, per theme. The caller supplies only
 * the list — every other decision (which tags, what blocks, how the report
 * reads) is made once, here, so no consumer can drift from another by editing
 * its own copy.
 */
export function defineA11ySpecs(manifest: readonly PageEntry[]): void {
    for (const entry of manifest) {
        for (const theme of THEMES) {
            test(`${entry.name} @ ${theme} — a11y`, async ({ page }, testInfo) => {
                await seedTheme(page, theme);
                await page.goto(withThemeGlobal(entry.path, theme));
                await page.waitForLoadState("networkidle");

                const results = await new AxeBuilder({ page })
                    .withTags(TAGS)
                    .disableRules(CONTRAST_RULES)
                    .analyze();

                await testInfo.attach("axe-results", {
                    body: JSON.stringify(results.violations, null, 2),
                    contentType: "application/json",
                });

                /**
                 * Anything axe could not decide, recorded rather than dropped.
                 *
                 * These do not fail the run, and the distinction is deliberate: an
                 * incomplete result here usually means a judgement no machine can
                 * make — whether link text reads sensibly in context, whether an
                 * image's description is accurate. Blocking on those would make the
                 * suite unpassable. Attaching them keeps the unknowns visible
                 * instead of letting a green run imply they were checked.
                 */
                if (results.incomplete.length > 0) {
                    await testInfo.attach("axe-incomplete", {
                        body: JSON.stringify(results.incomplete, null, 2),
                        contentType: "application/json",
                    });
                }

                const blocking = results.violations.filter((violation) => BLOCKING_IMPACTS.has(violation.impact ?? ""));
                expect(blocking, formatViolations(blocking)).toEqual([]);
            });
        }
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
