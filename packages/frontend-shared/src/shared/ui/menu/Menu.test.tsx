import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Menu } from "./Menu";
import { Button } from "../button";

function BasicMenu({ onItemClick }: { readonly onItemClick?: (label: string) => void } = {}) {
    return (
        <Menu.Root>
            <Menu.Trigger render={<Button tone="neutral">Actions</Button>} />
            <Menu.Popup>
                <Menu.Item onClick={() => onItemClick?.("Rename")}>Rename</Menu.Item>
                <Menu.Item onClick={() => onItemClick?.("Duplicate")}>Duplicate</Menu.Item>
                <Menu.Separator />
                <Menu.Item disabled onClick={() => onItemClick?.("Delete")}>
                    Delete
                </Menu.Item>
            </Menu.Popup>
        </Menu.Root>
    );
}

/**
 * jsdom's `fireEvent.click` defaults to `MouseEvent.detail: 0`. Base UI's
 * own `MenuRoot.mjs` explicitly reads that field to distinguish a keyboard-
 * originated activation (a real Enter/Space press on a `<button>` also
 * fires a `click` with `detail: 0`) from a real pointer click (`detail: 1`)
 * — verified by reading the source, then confirmed empirically: opening via
 * `detail: 0` highlights the first item immediately, opening via
 * `detail: 1` does not (matched exactly by a real Chromium run in
 * `packages/storybook/tests/e2e/menu-keyboard.spec.ts`, which found the
 * same split). Every "open via a real pointer click" test below opens with
 * `detail: 1` for that reason — using the bare default here would silently
 * test the keyboard path while claiming to test the mouse path.
 */
function openWithMouseClick(trigger: HTMLElement) {
    fireEvent.click(trigger, { detail: 1 });
}

describe("Menu", () => {
    it("renders the trigger as a named, real button (composed via render, per Button's own public API) and starts closed", () => {
        render(<BasicMenu />);

        const trigger = screen.getByRole("button", { name: "Actions" });
        expect(trigger.tagName).toBe("BUTTON");
        expect(trigger).toHaveClass("border-border-default"); // Button's own "neutral" tone class, confirming the render composition took effect.
        expect(trigger).toHaveAttribute("aria-haspopup", "menu");
        expect(trigger).toHaveAttribute("aria-expanded", "false");
        expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    });

    it("opens on a pointer click, exposing its items by role and name", () => {
        render(<BasicMenu />);

        openWithMouseClick(screen.getByRole("button", { name: "Actions" }));

        expect(screen.getByRole("button", { name: "Actions" })).toHaveAttribute("aria-expanded", "true");
        expect(screen.getByRole("menu")).toBeInTheDocument();
        expect(screen.getByRole("menuitem", { name: "Rename" })).toBeInTheDocument();
        expect(screen.getByRole("menuitem", { name: "Duplicate" })).toBeInTheDocument();
        expect(screen.getByRole("menuitem", { name: "Delete" })).toBeInTheDocument();
    });

    it("opens via a native button activation (Enter/Space — a click with detail: 0) and immediately highlights the first item", () => {
        render(<BasicMenu />);
        // No `detail` override: a real Enter/Space press on a native <button>
        // fires a `click` with `detail: 0`, exactly like this default does.
        fireEvent.click(screen.getByRole("button", { name: "Actions" }));

        const firstItem = screen.getByRole("menuitem", { name: "Rename" });
        expect(firstItem).toHaveAttribute("data-highlighted");
        expect(firstItem).toHaveAttribute("tabindex", "0");
    });

    it("opens via a pointer click WITHOUT highlighting any item — only keyboard navigation does that", () => {
        render(<BasicMenu />);
        openWithMouseClick(screen.getByRole("button", { name: "Actions" }));

        for (const item of screen.getAllByRole("menuitem")) {
            expect(item).not.toHaveAttribute("data-highlighted");
        }
    });

    it("closes on a second click of the trigger (toggle)", () => {
        render(<BasicMenu />);
        const trigger = screen.getByRole("button", { name: "Actions" });

        openWithMouseClick(trigger);
        expect(screen.getByRole("menu")).toBeInTheDocument();

        openWithMouseClick(trigger);
        expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    });

    it("closes when an item is clicked, and reports which one", () => {
        const onItemClick = vi.fn();
        render(<BasicMenu onItemClick={onItemClick} />);

        openWithMouseClick(screen.getByRole("button", { name: "Actions" }));
        fireEvent.click(screen.getByRole("menuitem", { name: "Rename" }));

        expect(onItemClick).toHaveBeenCalledWith("Rename");
        expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    });

    it("closes when a click lands outside the menu", () => {
        render(<BasicMenu />);
        openWithMouseClick(screen.getByRole("button", { name: "Actions" }));
        expect(screen.getByRole("menu")).toBeInTheDocument();

        fireEvent.mouseDown(document.body);
        fireEvent.click(document.body);

        expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    });

    it("does not activate a disabled item, and marks it data-disabled", () => {
        const onItemClick = vi.fn();
        render(<BasicMenu onItemClick={onItemClick} />);

        openWithMouseClick(screen.getByRole("button", { name: "Actions" }));
        const deleteItem = screen.getByRole("menuitem", { name: "Delete" });

        expect(deleteItem).toHaveAttribute("data-disabled");
        fireEvent.click(deleteItem);

        expect(onItemClick).not.toHaveBeenCalledWith("Delete");
        expect(screen.getByRole("menu")).toBeInTheDocument();
    });

    /**
     * Base UI's own `MenuRoot` passes `disabledIndices: []` (a hardcoded
     * empty array) to Floating UI's list navigation — verified by reading
     * `MenuRoot.mjs` directly — so a disabled item is deliberately still
     * reachable by arrow keys; it is only blocked from activating (the
     * test above). This matches the ARIA Authoring Practices menu pattern,
     * which allows a disabled menuitem to stay perceivable and navigable.
     */
    it("roving focus still reaches a disabled item — it is unreachable to activate, not to navigate to", () => {
        render(<BasicMenu />);
        openWithMouseClick(screen.getByRole("button", { name: "Actions" }));
        const menu = screen.getByRole("menu");

        fireEvent.keyDown(menu, { key: "End" });

        expect(screen.getByRole("menuitem", { name: "Delete" })).toHaveAttribute("data-highlighted");
    });

    it("renders a semantic separator between items", () => {
        render(<BasicMenu />);
        openWithMouseClick(screen.getByRole("button", { name: "Actions" }));

        expect(screen.getByRole("separator")).toBeInTheDocument();
    });

    it("renders a leading icon slot", () => {
        render(
            <Menu.Root>
                <Menu.Trigger render={<Button tone="neutral">Actions</Button>} />
                <Menu.Popup>
                    <Menu.Item leadingIcon={<span data-testid="leading-icon" />}>Rename</Menu.Item>
                </Menu.Popup>
            </Menu.Root>,
        );
        openWithMouseClick(screen.getByRole("button", { name: "Actions" }));

        expect(screen.getByTestId("leading-icon")).toBeInTheDocument();
    });

    it("renders a trailing shortcut wrapped in KeyboardKey (a real <kbd>)", () => {
        render(
            <Menu.Root>
                <Menu.Trigger render={<Button tone="neutral">Actions</Button>} />
                <Menu.Popup>
                    <Menu.Item shortcut="⌘C">Copy</Menu.Item>
                </Menu.Popup>
            </Menu.Root>,
        );
        openWithMouseClick(screen.getByRole("button", { name: "Actions" }));

        const item = screen.getByRole("menuitem", { name: "Copy⌘C" });
        const kbd = item.querySelector("kbd");
        expect(kbd).not.toBeNull();
        expect(kbd).toHaveTextContent("⌘C");
    });

    it("carries the focus-visible ring utility on every item, required of every interactive component in this system", () => {
        render(<BasicMenu />);
        openWithMouseClick(screen.getByRole("button", { name: "Actions" }));

        expect(screen.getByRole("menuitem", { name: "Rename" })).toHaveClass("focus-visible:focus-ring");
    });

    it("merges a caller-provided className on Menu.Item and Menu.Popup with their own classes", () => {
        render(
            <Menu.Root>
                <Menu.Trigger render={<Button tone="neutral">Actions</Button>} />
                <Menu.Popup className="mt-stack">
                    <Menu.Item className="mt-stack">Rename</Menu.Item>
                </Menu.Popup>
            </Menu.Root>,
        );
        openWithMouseClick(screen.getByRole("button", { name: "Actions" }));

        const item = screen.getByRole("menuitem", { name: "Rename" });
        expect(item).toHaveClass("rounded-control");
        expect(item).toHaveClass("mt-stack");
        expect(screen.getByRole("menu")).toHaveClass("mt-stack");
        expect(screen.getByRole("menu")).toHaveClass("rounded-card");
    });

    describe("keyboard reachability and navigation", () => {
        /**
         * A native `<button>` trigger opens on Enter/Space purely through
         * the browser's own activation behaviour — verified by reading
         * `@base-ui/react/internals/use-button/useButton.mjs` directly: its
         * manual keydown-to-click dispatch explicitly early-returns when
         * `isNativeButton` is true, deferring entirely to the platform.
         * jsdom does not implement that native activation itself (only a
         * real browser does), so the "press Enter/Space on the trigger"
         * path end-to-end is verified in a real browser by
         * `packages/storybook/tests/e2e/menu-keyboard.spec.ts` instead.
         * What IS verified here, in jsdom, is that the rendered trigger is
         * a real, focusable `<button>` — the element that native
         * Enter/Space handling requires — and (above) that a `click` event
         * shaped like the one Enter/Space produces (`detail: 0`) opens the
         * menu and highlights the first item, exactly as the real-browser
         * spec also finds.
         */
        it("is a real, focusable button — the element native Enter/Space activation depends on", () => {
            render(<BasicMenu />);
            const trigger = screen.getByRole("button", { name: "Actions" });

            trigger.focus();

            expect(trigger.tagName).toBe("BUTTON");
            expect(document.activeElement).toBe(trigger);
        });

        it("ArrowDown from a freshly-opened (unhighlighted) menu highlights the first item, then moves forward", () => {
            render(<BasicMenu />);
            openWithMouseClick(screen.getByRole("button", { name: "Actions" }));
            const menu = screen.getByRole("menu");

            fireEvent.keyDown(menu, { key: "ArrowDown" });
            expect(screen.getByRole("menuitem", { name: "Rename" })).toHaveAttribute("data-highlighted");

            fireEvent.keyDown(menu, { key: "ArrowDown" });
            expect(screen.getByRole("menuitem", { name: "Duplicate" })).toHaveAttribute("data-highlighted");
            expect(screen.getByRole("menuitem", { name: "Rename" })).not.toHaveAttribute("data-highlighted");
        });

        it("ArrowUp from a freshly-opened (unhighlighted) menu jumps straight to the last item", () => {
            render(<BasicMenu />);
            openWithMouseClick(screen.getByRole("button", { name: "Actions" }));
            const menu = screen.getByRole("menu");

            fireEvent.keyDown(menu, { key: "ArrowUp" });

            expect(screen.getByRole("menuitem", { name: "Delete" })).toHaveAttribute("data-highlighted");
        });

        it("ArrowUp from the first item loops to the last item (loopFocus default)", () => {
            render(<BasicMenu />);
            openWithMouseClick(screen.getByRole("button", { name: "Actions" }));
            const menu = screen.getByRole("menu");

            fireEvent.keyDown(menu, { key: "ArrowDown" });
            expect(screen.getByRole("menuitem", { name: "Rename" })).toHaveAttribute("data-highlighted");

            fireEvent.keyDown(menu, { key: "ArrowUp" });
            expect(screen.getByRole("menuitem", { name: "Delete" })).toHaveAttribute("data-highlighted");
        });

        it("Home and End jump to the first and last item", () => {
            render(<BasicMenu />);
            openWithMouseClick(screen.getByRole("button", { name: "Actions" }));
            const menu = screen.getByRole("menu");

            fireEvent.keyDown(menu, { key: "End" });
            expect(screen.getByRole("menuitem", { name: "Delete" })).toHaveAttribute("data-highlighted");

            fireEvent.keyDown(menu, { key: "Home" });
            expect(screen.getByRole("menuitem", { name: "Rename" })).toHaveAttribute("data-highlighted");
        });

        it("typeahead jumps to the item whose label starts with the typed character", () => {
            render(<BasicMenu />);
            openWithMouseClick(screen.getByRole("button", { name: "Actions" }));
            const menu = screen.getByRole("menu");

            fireEvent.keyDown(menu, { key: "d" });

            expect(screen.getByRole("menuitem", { name: "Duplicate" })).toHaveAttribute("data-highlighted");
        });

        it("Escape closes the menu and returns focus to the trigger", async () => {
            render(<BasicMenu />);
            const trigger = screen.getByRole("button", { name: "Actions" });
            openWithMouseClick(trigger);
            const menu = screen.getByRole("menu");

            fireEvent.keyDown(menu, { key: "Escape" });

            expect(screen.queryByRole("menu")).not.toBeInTheDocument();
            // Focus return happens after Base UI's own close transition
            // settles (verified live: asserting immediately after the
            // `keyDown` above still sees `document.body` focused) — real
            // async behaviour, not a jsdom limitation to paper over.
            await waitFor(() => expect(trigger).toHaveFocus());
        });
    });
});
