import { expect, test, type Locator } from "@playwright/test";
import { readStoryManifest } from "./story-manifest";

/**
 * Hand-written, not a per-manifest-entry loop — this spec exercises
 * `ToastRegion`'s dismiss and undo flow specifically, the one thing a
 * generic structural/visual pass cannot verify (real focus-gated
 * `aria-hidden` on the Close button, and the actual click-through path for
 * undo). Story ids resolved from the built manifest, the same convention
 * `menu-keyboard.spec.ts`/`drawer-keyboard.spec.ts` already established.
 */
function storyPath(id: string): string {
    const entry = readStoryManifest().find((candidate) => candidate.name === id);
    if (!entry) {
        throw new Error(`Story "${id}" was not found in the built Storybook manifest — did ToastRegion.stories.tsx's export names change?`);
    }
    return entry.path;
}

const DEFAULT_TOAST_STORY = "overlays-toastregion--default";
const WITH_UNDO_TOAST_STORY = "overlays-toastregion--with-undo";
const ALL_TONES_TOAST_STORY = "overlays-toastregion--all-tones";

/**
 * `Toast.Close` sets `aria-hidden={!expanded && !hasFocus}`, with `hasFocus`
 * as local state on the button itself (`ToastClose.js`) — a role-based
 * locator can never resolve it in its initial state, since Playwright's
 * accessibility-tree resolution (correctly) excludes `aria-hidden` nodes the
 * same way a screen reader would, matching the exact behaviour
 * `ToastRegion.test.tsx`'s jsdom investigation already found. A real user
 * dismisses it by hovering the toast (which expands the region) or tabbing
 * to it; this locates and focuses the raw element directly instead, then
 * clicks it once it is genuinely reachable.
 */
async function clickDismiss(toast: Locator): Promise<void> {
    const closeButton = toast.locator('button[aria-label="Dismiss"]');
    await closeButton.focus();
    await closeButton.click();
}

test.describe("ToastRegion — dismiss and undo", () => {
    test("firing a toast announces it as a dialog with its title", async ({ page }) => {
        await page.goto(storyPath(DEFAULT_TOAST_STORY));
        await page.getByRole("button", { name: "Simulate a failed save" }).click();

        const toast = page.getByRole("dialog", { name: "Couldn't save changes" });
        await expect(toast).toBeVisible();
    });

    test("clicking a toast's own Close button dismisses it, once it has focus", async ({ page }) => {
        await page.goto(storyPath(DEFAULT_TOAST_STORY));
        await page.getByRole("button", { name: "Simulate a failed save" }).click();
        const toast = page.getByRole("dialog", { name: "Couldn't save changes" });
        await expect(toast).toBeVisible();

        await clickDismiss(toast);

        await expect(toast).toBeHidden();
    });

    test("the undo action button triggers its callback without dismissing the original toast on its own", async ({ page }) => {
        await page.goto(storyPath(WITH_UNDO_TOAST_STORY));
        await page.getByRole("button", { name: "Archive application" }).click();

        const archivedToast = page.getByRole("dialog", { name: "Application archived" });
        await expect(archivedToast).toBeVisible();

        await archivedToast.getByRole("button", { name: "Undo" }).click();

        // The undo callback fires a second, distinct toast (per the story's
        // own wiring) — its appearance is the observable proof the click
        // reached the real callback, not just toggled some local state.
        await expect(page.getByRole("dialog", { name: "Restored" })).toBeVisible();
        // Undo does not itself close the toast it was clicked on — only an
        // explicit dismiss or the auto-dismiss timeout does.
        await expect(archivedToast).toBeVisible();
    });

    test("multiple toasts stack, and dismissing one leaves the others", async ({ page }) => {
        await page.goto(storyPath(ALL_TONES_TOAST_STORY));
        await page.getByRole("button", { name: "Neutral" }).click();
        await page.getByRole("button", { name: "Info" }).click();
        await page.getByRole("button", { name: "Success" }).click();

        const draftToast = page.getByRole("dialog", { name: "Draft saved" });
        const replyToast = page.getByRole("dialog", { name: "New reply" });
        const submittedToast = page.getByRole("dialog", { name: "Application submitted" });
        await expect(draftToast).toBeVisible();
        await expect(replyToast).toBeVisible();
        await expect(submittedToast).toBeVisible();

        await clickDismiss(replyToast);

        await expect(replyToast).toBeHidden();
        await expect(draftToast).toBeVisible();
        await expect(submittedToast).toBeVisible();
    });

    test("Escape dismisses a focused toast", async ({ page }) => {
        await page.goto(storyPath(DEFAULT_TOAST_STORY));
        await page.getByRole("button", { name: "Simulate a failed save" }).click();
        const toast = page.getByRole("dialog", { name: "Couldn't save changes" });
        await expect(toast).toBeVisible();

        await toast.focus();
        await page.keyboard.press("Escape");

        await expect(toast).toBeHidden();
    });
});
