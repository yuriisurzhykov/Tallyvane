import { expect, test } from "@playwright/test";
import { seedTheme, THEMES, withThemeGlobal } from "../utils/theme";
import { collectTextSamples, formatFindings, judge, type ContrastFinding } from "../utils/apca";
import type { PageEntry } from "../types";

/**
 * Contrast measured by APCA — the quality bar, not the compliance one.
 *
 * `defineWcagContrastSpecs` is the compliance one, and the two are deliberately
 * kept apart rather than merged into a single verdict. They come from different
 * models that disagree in both directions, and a combined result would hide
 * which of them objected — while making APCA impossible to drop.
 *
 * Dropping it is a live possibility, so the seams are worth stating. APCA is
 * research-grade rather than a standard: it was removed from the WCAG 3 draft
 * in 2023, WCAG 3 has not chosen a contrast algorithm, and the library's licence
 * is a limited one. Removing all of it means deleting this file, `utils/apca.ts`,
 * `utils/apca-w3.d.ts` and the `apca-w3` dependency. Nothing else refers to it.
 *
 * Why keep it at all: the WCAG 2 ratio is a formula that models paper. It is
 * symmetric, so it scores dark-on-light and light-on-dark identically even
 * though a self-illuminated screen does not show them identically, and it
 * accounts for size and weight only through one coarse threshold. It passes
 * combinations that are hard to read.
 *
 * Two consequences for reading these failures. There is no single pass mark:
 * the report says what size the text would need at its measured contrast and
 * weight, so the fix is a darker colour, a larger size or a heavier weight,
 * whichever suits the design. And it runs per theme, because a monochrome
 * accent inverts and the two directions are genuinely different measurements.
 */
export function defineApcaContrastSpecs(manifest: readonly PageEntry[]): void {
    for (const entry of manifest) {
        for (const theme of THEMES) {
            test(`${entry.name} @ ${theme} — contrast (APCA)`, async ({ page }, testInfo) => {
                await seedTheme(page, theme);
                await page.goto(withThemeGlobal(entry.path, theme));
                await page.waitForLoadState("networkidle");

                const samples = await collectTextSamples(page);

                // A page that yields nothing has not been measured, and an empty
                // list of failures would otherwise read as a pass.
                expect(samples.length, "no text was found to measure").toBeGreaterThan(0);

                const findings = samples
                    .map(judge)
                    .filter((finding): finding is ContrastFinding => finding !== null);

                // Both are attached, and they answer different questions. The
                // samples say what was measured, which is what makes a pass
                // meaningful rather than a sign the walker found nothing; the
                // findings say what failed, and are what the run summary digests.
                await testInfo.attach("apca-samples", {
                    body: JSON.stringify(samples, null, 2),
                    contentType: "application/json",
                });
                await testInfo.attach("apca-findings", {
                    body: JSON.stringify(findings, null, 2),
                    contentType: "application/json",
                });

                expect(findings, formatFindings(findings)).toEqual([]);
            });
        }
    }
}
