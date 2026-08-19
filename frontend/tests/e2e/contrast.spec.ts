import { expect, test } from "@playwright/test";
import { pagesManifest } from "./pages.manifest";
import { seedTheme, THEMES } from "./utils/theme";
import { collectTextSamples, formatFindings, judge, type ContrastFinding } from "./utils/contrast";

/**
 * Contrast, measured by APCA rather than by the WCAG 2 ratio.
 *
 * The ratio it replaces is a formula from 1988 that models paper. It is
 * symmetric, so it scores dark-on-light and light-on-dark identically even
 * though a self-illuminated screen does not display them identically, and it
 * takes no account of size or weight beyond one coarse threshold. In practice
 * it passes combinations that are hard to read and fails ones that are fine —
 * which is exactly what the successor exists to correct.
 *
 * Two consequences for reading these failures. There is no single pass mark:
 * the report says what size the text would have to be at its measured contrast
 * and weight, so the fix is a darker colour OR a larger size OR a heavier
 * weight, whichever suits the design. And it runs per theme, because a
 * monochrome accent inverts and the two directions are genuinely different
 * measurements, not the same one twice.
 */
for (const entry of pagesManifest) {
    for (const theme of THEMES) {
        test(`${entry.name} @ ${theme} — APCA contrast`, async ({ page }, testInfo) => {
            await seedTheme(page, theme);
            await page.goto(entry.path);
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
