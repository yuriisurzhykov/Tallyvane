import { expect, test } from "@playwright/test";
import { readStoryManifest } from "./story-manifest";

/**
 * Hand-written, not a per-manifest-entry loop like `a11y.spec.ts`/
 * `visual.spec.ts` — this spec exercises `Menu`'s own keyboard/focus
 * machinery specifically, the one thing a generic structural/visual pass
 * cannot verify (real DOM focus movement, roving `tabIndex`, typeahead).
 * Story ids are resolved from the built manifest rather than hardcoded, so
 * a rename of `Menu.stories.tsx`'s exports here fails loudly instead of
 * silently pointing at a stale path.
 */
function storyPath(id: string): string {
    const entry = readStoryManifest().find((candidate) => candidate.name === id);
    if (!entry) {
        throw new Error(`Story "${id}" was not found in the built Storybook manifest — did Menu.stories.tsx's export names change?`);
    }
    return entry.path;
}

const DEFAULT_MENU_STORY = "actions-menu--default";
const DISABLED_ITEM_MENU_STORY = "actions-menu--with-disabled-item";

test.describe("Menu — keyboard and focus", () => {
    /**
     * A real pointer click does NOT pre-highlight any item — only a
     * keyboard-style activation does (the next test). Discovered live, not
     * assumed: an earlier draft of this spec asserted the opposite and
     * failed against a real Chromium run, which is exactly why this file
     * exists rather than trusting `Menu.test.tsx`'s jsdom result alone —
     * jsdom's `fireEvent.click` defaults to `MouseEvent.detail: 0`, which
     * Base UI treats as a keyboard-style click (see `Menu.test.tsx`'s own
     * comment on this), silently testing the wrong path unless overridden.
     * A real `page.click()` reliably reproduces a genuine pointer click.
     */
    test("a pointer click opens the menu and moves focus into the popup, without pre-highlighting an item", async ({ page }) => {
        await page.goto(storyPath(DEFAULT_MENU_STORY));
        await page.getByRole("button", { name: "Actions" }).click();

        const menu = page.getByRole("menu");
        await expect(menu).toBeVisible();
        await expect(menu).toBeFocused();
        for (const label of ["Rename", "Duplicate", "Delete"]) {
            await expect(page.getByRole("menuitem", { name: label })).not.toHaveAttribute("data-highlighted", "");
        }
    });

    /**
     * `Menu.test.tsx` (Vitest, jsdom) deliberately does not assert the
     * Enter/Space path itself: a native `<button>` opens on Enter/Space
     * purely through the browser's own activation behaviour, which jsdom
     * does not implement. This is that path, verified for real, and it
     * also confirms the first item IS highlighted immediately — the
     * keyboard-activation counterpart to the pointer-click test above.
     */
    test("opens on Enter and on Space — the browser's native button activation — and highlights the first item", async ({ page }) => {
        await page.goto(storyPath(DEFAULT_MENU_STORY));
        const trigger = page.getByRole("button", { name: "Actions" });

        await trigger.focus();
        await page.keyboard.press("Enter");
        await expect(page.getByRole("menu")).toBeVisible();
        const rename = page.getByRole("menuitem", { name: "Rename" });
        await expect(rename).toHaveAttribute("data-highlighted", "");
        await expect(rename).toHaveAttribute("tabindex", "0");
        await expect(rename).toBeFocused();

        await page.keyboard.press("Escape");
        await expect(page.getByRole("menu")).toBeHidden();

        await trigger.focus();
        await page.keyboard.press("Space");
        await expect(page.getByRole("menu")).toBeVisible();
        await expect(page.getByRole("menuitem", { name: "Rename" })).toHaveAttribute("data-highlighted", "");
    });

    test("ArrowDown/ArrowUp move the roving tabindex through the items", async ({ page }) => {
        await page.goto(storyPath(DEFAULT_MENU_STORY));
        await page.getByRole("button", { name: "Actions" }).click();

        // Opened via a pointer click — nothing highlighted yet, so the
        // first ArrowDown lands on the first item rather than the second.
        await page.keyboard.press("ArrowDown");
        const rename = page.getByRole("menuitem", { name: "Rename" });
        await expect(rename).toHaveAttribute("data-highlighted", "");
        await expect(rename).toHaveAttribute("tabindex", "0");
        await expect(rename).toBeFocused();

        await page.keyboard.press("ArrowDown");
        const duplicate = page.getByRole("menuitem", { name: "Duplicate" });
        await expect(duplicate).toHaveAttribute("data-highlighted", "");
        await expect(duplicate).toBeFocused();
        await expect(rename).toHaveAttribute("tabindex", "-1");

        await page.keyboard.press("ArrowUp");
        await expect(rename).toHaveAttribute("data-highlighted", "");
        await expect(rename).toBeFocused();
    });

    test("typeahead jumps to the item whose label starts with the typed character", async ({ page }) => {
        await page.goto(storyPath(DEFAULT_MENU_STORY));
        await page.getByRole("button", { name: "Actions" }).click();

        // Base UI focuses the popup on the next animation frame (`enqueueFocus`);
        // `page.keyboard.press` before that misses the typeahead handler.
        const menu = page.getByRole("menu");
        await expect(menu).toBeVisible();
        await expect(menu).toBeFocused();
        await menu.press("d");

        const duplicate = page.getByRole("menuitem", { name: "Duplicate" });
        await expect(duplicate).toHaveAttribute("data-highlighted", "");
        await expect(duplicate).toBeFocused();
    });

    test("Home and End jump to the first and last item", async ({ page }) => {
        await page.goto(storyPath(DEFAULT_MENU_STORY));
        await page.getByRole("button", { name: "Actions" }).click();

        await page.keyboard.press("End");
        const last = page.getByRole("menuitem", { name: "Delete" });
        await expect(last).toHaveAttribute("data-highlighted", "");
        await expect(last).toBeFocused();

        await page.keyboard.press("Home");
        const first = page.getByRole("menuitem", { name: "Rename" });
        await expect(first).toHaveAttribute("data-highlighted", "");
        await expect(first).toBeFocused();
    });

    test("Escape closes the menu and returns focus to the trigger", async ({ page }) => {
        await page.goto(storyPath(DEFAULT_MENU_STORY));
        const trigger = page.getByRole("button", { name: "Actions" });
        await trigger.click();
        await expect(page.getByRole("menu")).toBeVisible();

        await page.keyboard.press("Escape");

        await expect(page.getByRole("menu")).toBeHidden();
        await expect(trigger).toBeFocused();
    });

    test("clicking an item closes the menu", async ({ page }) => {
        await page.goto(storyPath(DEFAULT_MENU_STORY));
        await page.getByRole("button", { name: "Actions" }).click();

        await page.getByRole("menuitem", { name: "Rename" }).click();

        await expect(page.getByRole("menu")).toBeHidden();
    });

    test("clicking outside the menu closes it", async ({ page }) => {
        await page.goto(storyPath(DEFAULT_MENU_STORY));
        await page.getByRole("button", { name: "Actions" }).click();
        await expect(page.getByRole("menu")).toBeVisible();

        await page.mouse.click(5, 5);

        await expect(page.getByRole("menu")).toBeHidden();
    });

    test("a disabled item is reachable by keyboard but not activatable", async ({ page }) => {
        await page.goto(storyPath(DISABLED_ITEM_MENU_STORY));
        await page.getByRole("button", { name: "Actions" }).click();

        const disabledItem = page.getByRole("menuitem", { name: "Duplicate (unavailable)" });
        await expect(disabledItem).toHaveAttribute("data-disabled", "");

        // Arrow navigation still reaches it (ARIA menu items may stay
        // navigable while disabled) — this is Base UI's own behaviour,
        // verified against `MenuRoot.mjs`'s hardcoded `disabledIndices: []`.
        // It is the second item ("Rename" is first, unhighlighted after a
        // pointer-click open), so this takes two ArrowDown presses.
        await page.keyboard.press("ArrowDown");
        await page.keyboard.press("ArrowDown");
        await expect(disabledItem).toHaveAttribute("data-highlighted", "");

        await page.keyboard.press("Enter");
        await expect(page.getByRole("menu")).toBeVisible();
    });
});
