import { describe, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { fireEvent } from "@testing-library/dom";
import { Drawer } from "./Drawer";

function renderDrawer() {
    return render(
        <>
            <button>Outside button</button>
            <Drawer.Root>
                <Drawer.Trigger>Add job</Drawer.Trigger>
                <Drawer.Popup>
                    <Drawer.Title>Add job</Drawer.Title>
                    <Drawer.Close label="Close drawer" />
                    <button>Save</button>
                </Drawer.Popup>
            </Drawer.Root>
        </>,
    );
}

describe("Drawer", () => {
    it("is closed until the trigger is activated", () => {
        renderDrawer();
        expect(screen.queryByText("Add job", { selector: "h2" })).not.toBeInTheDocument();
    });

    it("opens on trigger click and shows its title and content", async () => {
        renderDrawer();
        fireEvent.click(screen.getByRole("button", { name: "Add job" }));

        await waitFor(() => {
            expect(screen.getByRole("heading", { name: "Add job" })).toBeInTheDocument();
        });
        expect(screen.getByRole("button", { name: "Save" })).toBeInTheDocument();
    });

    /**
     * Load-bearing per `COMPONENTS.md` — verified directly, not assumed:
     * `modal` defaults to `true` (`DrawerRoot.d.ts`), which traps focus
     * inside the popup. Confirmed by rendering, not inferred from the prop
     * default — Base UI marks the rest of the page `data-base-ui-inert`
     * plus `aria-hidden="true"` while the drawer is open, which is what
     * actually drops "Outside button" out of both the accessibility tree
     * and tab order; `getByRole` correctly fails to find it below, and that
     * failure *is* the trap working, not a broken query.
     */
    it("traps focus inside the popup while open, sending the rest of the page inert", async () => {
        renderDrawer();
        fireEvent.click(screen.getByRole("button", { name: "Add job" }));
        await waitFor(() => expect(screen.getByRole("heading", { name: "Add job" })).toBeInTheDocument());

        const dialog = screen.getByRole("dialog");
        const outsideButton = screen.getByText("Outside button");

        /**
         * Base UI moves focus to the first tabbable element inside on open,
         * per its own docs — but "tabbable" is measured via element
         * dimensions (`getClientRects`), which jsdom always reports as zero
         * for every element, so its tabbable-element search here correctly
         * (and per Base UI's own documented fallback) finds none and
         * focuses the dialog container itself instead. Either target is
         * "inside the drawer, not on the trigger or the page behind it" —
         * the property this test actually needs — so it asserts that,
         * rather than which exact element a real browser would pick.
         */
        await waitFor(() => {
            expect(dialog.contains(document.activeElement)).toBe(true);
        });

        expect(outsideButton.closest("[data-base-ui-inert]")).not.toBeNull();
        expect(outsideButton.closest('[aria-hidden="true"]')).not.toBeNull();
        expect(screen.queryByRole("button", { name: "Outside button" })).not.toBeInTheDocument();
    });

    it("returns focus to the trigger after closing", async () => {
        renderDrawer();
        const trigger = screen.getByRole("button", { name: "Add job" });
        fireEvent.click(trigger);
        await waitFor(() => expect(screen.getByRole("heading", { name: "Add job" })).toBeInTheDocument());

        fireEvent.click(screen.getByRole("button", { name: "Close drawer" }));

        await waitFor(() => {
            expect(trigger).toHaveFocus();
        });
    });

    it("closes on Escape", async () => {
        renderDrawer();
        fireEvent.click(screen.getByRole("button", { name: "Add job" }));
        await waitFor(() => expect(screen.getByRole("heading", { name: "Add job" })).toBeInTheDocument());

        fireEvent.keyDown(screen.getByRole("heading", { name: "Add job" }), { key: "Escape" });

        await waitFor(() => {
            expect(screen.queryByRole("heading", { name: "Add job" })).not.toBeInTheDocument();
        });
    });

    it("renders a backdrop and panel on separate stacking layers (scrim below modal)", async () => {
        const { container } = renderDrawer();
        fireEvent.click(screen.getByRole("button", { name: "Add job" }));
        await waitFor(() => expect(screen.getByRole("heading", { name: "Add job" })).toBeInTheDocument());

        expect(container.ownerDocument.querySelector(".z-scrim")).toBeInTheDocument();
        expect(container.ownerDocument.querySelector(".z-modal")).toBeInTheDocument();
    });

    it("defaults to sliding in from the right, overriding Base UI's own 'down' bottom-sheet default", async () => {
        renderDrawer();
        fireEvent.click(screen.getByRole("button", { name: "Add job" }));

        await waitFor(() => {
            expect(screen.getByRole("dialog")).toHaveAttribute("data-swipe-direction", "right");
        });
    });
});
