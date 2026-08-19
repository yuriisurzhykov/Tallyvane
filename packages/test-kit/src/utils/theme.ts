import type { Page } from "@playwright/test";

export type ThemeName = "dark" | "light";

export const THEMES: readonly ThemeName[] = ["dark", "light"];

/**
 * Must match `THEME_STORAGE_KEY` in
 * `packages/frontend-shared/src/shared/ui/theme/provider/constants.ts`.
 * */
const THEME_STORAGE_KEY = "tallyvane.theme-preference";

/**
 * Forces a theme before the page renders at all, by seeding the same
 * localStorage key the app reads.
 *
 * It must be called BEFORE `page.goto`. `addInitScript` runs ahead of every
 * script the page owns, including the inline pre-hydration one — which is what
 * applies the theme synchronously, before the first paint.
 *
 * Waiting instead of seeding does not work, and the reason is worth knowing:
 * the provider's own effects also apply the theme, but they run after hydration
 * and after the first paint, and nothing a test can wait for corresponds to a
 * React effect. `networkidle` tracks the network and nothing else. A suite that
 * relies on those effects lands inside that window at random and produces a
 * flake that looks like a visual regression.
 */
export async function seedTheme(page: Page, theme: ThemeName): Promise<void> {
    await page.addInitScript(
        ([key, value]) => {
            window.localStorage.setItem(key, value);
        },
        [THEME_STORAGE_KEY, theme] as const,
    );
}
