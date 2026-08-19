import { expect, test } from "@playwright/test";
import { pagesManifest } from "./pages.manifest";
import { seedTheme, THEMES } from "./utils/theme";

for (const entry of pagesManifest) {
    for (const theme of THEMES) {
        test(`${ entry.name } @ ${ theme }`, async ({ page }) => {
            await seedTheme(page, theme);
            await page.goto(entry.path);
            await page.waitForLoadState("networkidle");

            // Passing an array puts the shot in a folder per page, so the
            // baselines read as `storybook/dark-desktop.png` rather than as a
            // flat pile of names nobody can scan.
            await expect(page).toHaveScreenshot([entry.name, `${ theme }.png`], { fullPage: true });
        });
    }
}
