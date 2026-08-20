import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { fireEvent } from "@testing-library/dom";
import { ContextMenu } from "./ContextMenu";

function BasicContextMenu({ onItemClick }: { readonly onItemClick?: (label: string) => void } = {}) {
    return (
        <ContextMenu.Root>
            <ContextMenu.Trigger>Table row</ContextMenu.Trigger>
            <ContextMenu.Popup>
                <ContextMenu.Item onClick={() => onItemClick?.("Open job")}>Open job</ContextMenu.Item>
                <ContextMenu.Item onClick={() => onItemClick?.("Log event")}>Log event</ContextMenu.Item>
                <ContextMenu.Separator />
                <ContextMenu.Item disabled onClick={() => onItemClick?.("Archive")}>
                    Archive
                </ContextMenu.Item>
            </ContextMenu.Popup>
        </ContextMenu.Root>
    );
}

function openWithRightClick(trigger: Element): void {
    fireEvent.contextMenu(trigger);
}

describe("ContextMenu", () => {
    it("is closed until the trigger area is right-clicked", () => {
        render(<BasicContextMenu />);
        expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    });

    it("opens on right-click, exposing its items by role and name", () => {
        render(<BasicContextMenu />);
        openWithRightClick(screen.getByText("Table row"));

        expect(screen.getByRole("menu")).toBeInTheDocument();
        expect(screen.getByRole("menuitem", { name: "Open job" })).toBeInTheDocument();
        expect(screen.getByRole("menuitem", { name: "Log event" })).toBeInTheDocument();
        expect(screen.getByRole("menuitem", { name: "Archive" })).toBeInTheDocument();
    });

    it("renders a semantic separator between items", () => {
        render(<BasicContextMenu />);
        openWithRightClick(screen.getByText("Table row"));

        expect(screen.getByRole("separator")).toBeInTheDocument();
    });

    it("closes when an item is clicked, and reports which one", () => {
        const onItemClick = vi.fn();
        render(<BasicContextMenu onItemClick={onItemClick} />);
        openWithRightClick(screen.getByText("Table row"));

        fireEvent.click(screen.getByRole("menuitem", { name: "Open job" }));

        expect(onItemClick).toHaveBeenCalledWith("Open job");
        expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    });

    it("closes when a click lands outside the menu", () => {
        render(<BasicContextMenu />);
        openWithRightClick(screen.getByText("Table row"));
        expect(screen.getByRole("menu")).toBeInTheDocument();

        fireEvent.mouseDown(document.body);
        fireEvent.click(document.body);

        expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    });

    it("does not activate a disabled item, and marks it data-disabled", () => {
        const onItemClick = vi.fn();
        render(<BasicContextMenu onItemClick={onItemClick} />);
        openWithRightClick(screen.getByText("Table row"));
        const archiveItem = screen.getByRole("menuitem", { name: "Archive" });

        expect(archiveItem).toHaveAttribute("data-disabled");
        fireEvent.click(archiveItem);

        expect(onItemClick).not.toHaveBeenCalledWith("Archive");
        expect(screen.getByRole("menu")).toBeInTheDocument();
    });

    describe("keyboard reachability and navigation — same underlying MenuRoot as Menu.tsx", () => {
        it("ArrowDown moves the roving highlight forward through the items", () => {
            render(<BasicContextMenu />);
            openWithRightClick(screen.getByText("Table row"));
            const menu = screen.getByRole("menu");

            fireEvent.keyDown(menu, { key: "ArrowDown" });
            expect(screen.getByRole("menuitem", { name: "Open job" })).toHaveAttribute("data-highlighted");

            fireEvent.keyDown(menu, { key: "ArrowDown" });
            expect(screen.getByRole("menuitem", { name: "Log event" })).toHaveAttribute("data-highlighted");
        });

        it("a disabled item is reachable by keyboard but not activatable", () => {
            render(<BasicContextMenu />);
            openWithRightClick(screen.getByText("Table row"));
            const menu = screen.getByRole("menu");

            fireEvent.keyDown(menu, { key: "End" });

            expect(screen.getByRole("menuitem", { name: "Archive" })).toHaveAttribute("data-highlighted");
        });

        it("typeahead jumps to the item whose label starts with the typed character", () => {
            render(<BasicContextMenu />);
            openWithRightClick(screen.getByText("Table row"));
            const menu = screen.getByRole("menu");

            fireEvent.keyDown(menu, { key: "l" });

            expect(screen.getByRole("menuitem", { name: "Log event" })).toHaveAttribute("data-highlighted");
        });

        it("Escape closes the menu", async () => {
            render(<BasicContextMenu />);
            openWithRightClick(screen.getByText("Table row"));
            const menu = screen.getByRole("menu");

            fireEvent.keyDown(menu, { key: "Escape" });

            await waitFor(() => {
                expect(screen.queryByRole("menu")).not.toBeInTheDocument();
            });
        });
    });
});
