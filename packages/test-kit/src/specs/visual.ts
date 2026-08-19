import { expect, test } from "@playwright/test";
import { seedTheme, THEMES } from "../utils/theme";
import type { PageEntry } from "../types";

/**
 * One full-page screenshot per entry, per theme.
 *
 * "Full page" is also "per component" once the caller is `packages/storybook`:
 * a story's `iframe.html?id=...` renders exactly one component in exactly one
 * state, filling the page, so there is no separate "screenshot just this
 * section" mechanism to maintain — the isolation Storybook already provides is
 * the isolation this spec needs.
 */
export function defineVisualSpecs(manifest: readonly PageEntry[]): void {
    for (const entry of manifest) {
        for (const theme of THEMES) {
            test(`${entry.name} @ ${theme}`, async ({ page }) => {
                await seedTheme(page, theme);
                await page.goto(entry.path);
                await page.waitForLoadState("networkidle");

                // Passing an array puts the shot in a folder per entry, so the
                // baselines read as `<name>/dark-desktop.png` rather than as a
                // flat pile of names nobody can scan.
                await expect(page).toHaveScreenshot([entry.name, `${theme}.png`], { fullPage: true });
            });
        }
    }
}
