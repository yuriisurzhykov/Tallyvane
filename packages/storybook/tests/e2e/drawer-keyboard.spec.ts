import { expect, test } from "@playwright/test";
import { readStoryManifest } from "./story-manifest";

/**
 * Hand-written, not a per-manifest-entry loop like `a11y.spec.ts`/
 * `visual.spec.ts` — this spec exercises `Drawer`'s own focus machinery
 * specifically: the load-bearing behaviour `COMPONENTS.md` calls out by
 * name ("**The creation surface.**"), which a generic structural/visual
 * pass cannot verify (real DOM focus trapping, `inert`, focus return).
 * Story ids are resolved from the built manifest rather than hardcoded,
 * the same convention `menu-keyboard.spec.ts` already established.
 */
function storyPath(id: string): string {
    const entry = readStoryManifest().find((candidate) => candidate.name === id);
    if (!entry) {
        throw new Error(`Story "${id}" was not found in the built Storybook manifest — did Drawer.stories.tsx's export names change?`);
    }
    return entry.path;
}

const DEFAULT_DRAWER_STORY = "overlays-drawer--default";
const WITH_CLOSE_DRAWER_STORY = "overlays-drawer--with-close";

test.describe("Drawer — keyboard and focus", () => {
    test("opens on trigger click and moves focus into the popup", async ({ page }) => {
        await page.goto(storyPath(DEFAULT_DRAWER_STORY));
        await page.getByRole("button", { name: "Add job" }).click();

        const dialog = page.getByRole("dialog");
        await expect(dialog).toBeVisible();
        await expect(dialog).toContainText("Add job");
    });

    /**
     * The load-bearing case, verified live rather than assumed: `modal`
     * defaults to `true` (`DrawerRoot.d.ts`), which Base UI implements by
     * marking the rest of the page `inert` while the drawer is open.
     * `Drawer.test.tsx` (Vitest, jsdom) already confirms this at the
     * attribute level; this is the same claim confirmed in a real browser,
     * where `inert` genuinely removes an element from both the
     * accessibility tree and the tab order rather than jsdom's own
     * approximation of one.
     */
    test("traps focus: the trigger's container becomes inert, and Tab cycles inside the drawer without ever reaching it", async ({ page }) => {
        await page.goto(storyPath(WITH_CLOSE_DRAWER_STORY));
        // A raw CSS locator, not `getByRole`: once the drawer marks this
        // button's ancestor `aria-hidden`, a role-based locator can no
        // longer resolve it at all (the same behaviour `Popover.test.tsx`'s
        // jsdom queries hit) — this test needs to inspect that exact
        // now-hidden state, so it has to find the element a way that keeps
        // working after it happens.
        const trigger = page.locator("button", { hasText: "Log interview" });
        await trigger.click();

        await expect(page.getByRole("dialog")).toBeVisible();

        // The trigger's own ancestor, still present in the DOM, is marked
        // real `inert` — the same attribute-level claim `Drawer.test.tsx`
        // verifies in jsdom, confirmed here against a real browser's actual
        // tab-order and accessibility-tree behaviour, not jsdom's
        // approximation of it.
        const triggerAncestorIsInert = await trigger.evaluate((el) => !!el.closest("[data-base-ui-inert]"));
        expect(triggerAncestorIsInert).toBe(true);

        /**
         * Tab repeatedly; focus must always settle back inside the dialog.
         * Base UI implements the wrap-around with `data-base-ui-focus-guard`
         * spans that sit just outside `[role="dialog"]` in the DOM (visible
         * in this test's own error-context snapshot) and redirect focus
         * back in via their own `onFocus` handler — a real, if momentary,
         * step through a sibling of the dialog, not a bug. `expect.poll`
         * gives that redirect a moment to run rather than asserting on the
         * instant right after the keypress.
         */
        for (let i = 0; i < 6; i += 1) {
            await page.keyboard.press("Tab");
            await expect
                .poll(() =>
                    page.evaluate(() => {
                        const dialogEl = document.querySelector('[role="dialog"]');
                        return !!dialogEl && dialogEl.contains(document.activeElement);
                    }),
                )
                .toBe(true);
        }
    });

    test("closing via the Close button returns focus to the trigger", async ({ page }) => {
        await page.goto(storyPath(WITH_CLOSE_DRAWER_STORY));
        const trigger = page.getByRole("button", { name: "Log interview" });
        await trigger.click();

        await expect(page.getByRole("dialog")).toBeVisible();
        await page.getByRole("button", { name: "Close drawer" }).click();

        await expect(page.getByRole("dialog")).toBeHidden();
        await expect(trigger).toBeFocused();
    });

    test("Escape closes the drawer and returns focus to the trigger", async ({ page }) => {
        await page.goto(storyPath(DEFAULT_DRAWER_STORY));
        const trigger = page.getByRole("button", { name: "Add job" });
        await trigger.click();
        await expect(page.getByRole("dialog")).toBeVisible();

        await page.keyboard.press("Escape");

        await expect(page.getByRole("dialog")).toBeHidden();
        await expect(trigger).toBeFocused();
    });

    test("clicking the backdrop closes the drawer", async ({ page }) => {
        await page.goto(storyPath(DEFAULT_DRAWER_STORY));
        await page.getByRole("button", { name: "Add job" }).click();
        await expect(page.getByRole("dialog")).toBeVisible();

        // The backdrop covers the rest of the viewport; a corner is never
        // covered by the drawer panel itself, which is pinned to the right.
        await page.mouse.click(5, 5);

        await expect(page.getByRole("dialog")).toBeHidden();
    });
});
