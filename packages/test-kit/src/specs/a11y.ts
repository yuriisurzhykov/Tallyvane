import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { seedTheme, THEMES, withThemeGlobal } from "../utils/theme";
import type { PageEntry } from "../types";

type AxeViolation = Awaited<ReturnType<AxeBuilder["analyze"]>>["violations"][number];

/**
 * Which kind of thing the spec is looking at. `"document"` is a real page
 * (`frontend-web`, later `frontend-admin`); `"component"` is an isolated
 * Storybook story. The closed page-scoped rule list below is owned here, not
 * passed in as a free-form array, so no consumer can pick a private subset
 * and drift from another.
 */
export type A11ySurface = "document" | "component";

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
 * Document/page questions. A Button story is not a page: wrapping every
 * iframe in `<main>`/`<h1>` would fake a surface the product does not have,
 * and a green run built on that fake would be the one outcome worse than a
 * skipped rule. Name/role/label rules stay on — those are why axe runs on
 * stories at all.
 */
const PAGE_SCOPED_RULES = [
    "page-has-heading-one",
    "landmark-one-main",
    "bypass",
    "document-title",
    "region",
] as const;

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

function disabledRules(surface: A11ySurface): string[] {
    return surface === "component" ? [...CONTRAST_RULES, ...PAGE_SCOPED_RULES] : [...CONTRAST_RULES];
}

/**
 * Registers one `test()` per page/story, per theme. The caller supplies the
 * list and, for isolated stories, `{ surface: "component" }` — every other
 * decision (which tags, which rules a surface skips, that remaining
 * violations fail) is made once, here, so no consumer can drift from another
 * by editing its own copy.
 *
 * Remaining `violations[]` fail the test. A first draft filtered to
 * `critical`/`serious` and attached the rest: that produced a successful CI
 * job with 1458 `moderate` findings, almost all `landmark-one-main` and
 * `page-has-heading-one` on Storybook stories. `axe-incomplete` still does
 * not fail — those are judgements a machine cannot make, and blocking on
 * them would make the suite unpassable.
 */
export function defineA11ySpecs(
    manifest: readonly PageEntry[],
    options: { readonly surface?: A11ySurface } = {},
): void {
    const surface = options.surface ?? "document";
    const disabled = disabledRules(surface);

    for (const entry of manifest) {
        for (const theme of THEMES) {
            test(`${entry.name} @ ${theme} — a11y`, async ({ page }, testInfo) => {
                await seedTheme(page, theme);
                await page.goto(withThemeGlobal(entry.path, theme));
                await page.waitForLoadState("networkidle");

                const results = await new AxeBuilder({ page })
                    .withTags(TAGS)
                    .disableRules(disabled)
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

                expect(results.violations, formatViolations(results.violations)).toEqual([]);
            });
        }
    }
}

function formatViolations(violations: readonly AxeViolation[]): string {
    if (violations.length === 0) return "";
    const lines = violations.map((violation) => {
        const where = violation.nodes.map((node) => node.target.join(" ")).join(", ");
        return `- [${String(violation.impact)}] ${violation.id}: ${violation.help} (${where})`;
    });
    return `Accessibility violations found:\n${lines.join("\n")}`;
}
