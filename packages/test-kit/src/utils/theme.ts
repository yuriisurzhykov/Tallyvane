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

/**
 * `seedTheme` alone is a no-op for Storybook: its preview drives the theme
 * from a toolbar "global" (`context.globals.theme`, defaulting to `"dark"`
 * in `preview.tsx`), never from `localStorage` — confirmed by rendering the
 * same story with and without this query param and reading
 * `document.documentElement.className` back (`theme-dark` either way,
 * without it, regardless of which theme a test thinks it seeded). Every
 * screenshot/scan taken against `packages/storybook` before this existed
 * silently measured dark theme only, `@ light` runs included.
 *
 * `globals=<name>:<value>` in the URL is Storybook's own documented
 * mechanism for setting a global without going through its UI, and it
 * resolves identically on the `iframe.html` preview URL used here. Harmless
 * on `frontend-web`'s own routes — an unrecognized query parameter there is
 * simply ignored — so every consumer of this function can call it
 * unconditionally rather than branching on which kind of page it is.
 */
export function withThemeGlobal(path: string, theme: ThemeName): string {
    const separator = path.includes("?") ? "&" : "?";
    return `${path}${separator}globals=theme:${theme}`;
}
