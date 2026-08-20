import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { fireEvent } from "@testing-library/dom";
import { Popover } from "./Popover";

function renderPopover() {
    return render(
        <Popover.Root>
            <Popover.Trigger>Filter jobs</Popover.Trigger>
            <Popover.Popup>
                <p>Panel content</p>
            </Popover.Popup>
        </Popover.Root>,
    );
}

describe("Popover", () => {
    it("is closed until the trigger is activated", () => {
        renderPopover();
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    it("opens on trigger click and exposes the panel with role dialog", async () => {
        renderPopover();
        fireEvent.click(screen.getByRole("button", { name: "Filter jobs" }));

        await waitFor(() => {
            expect(screen.getByRole("dialog")).toBeInTheDocument();
        });
        expect(screen.getByText("Panel content")).toBeInTheDocument();
    });

    it("closes on Escape", async () => {
        renderPopover();
        fireEvent.click(screen.getByRole("button", { name: "Filter jobs" }));
        await waitFor(() => expect(screen.getByRole("dialog")).toBeInTheDocument());

        fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });

        await waitFor(() => {
            expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
        });
    });

    it("closes on an outside click", async () => {
        renderPopover();
        fireEvent.click(screen.getByRole("button", { name: "Filter jobs" }));
        await waitFor(() => expect(screen.getByRole("dialog")).toBeInTheDocument());

        fireEvent.mouseDown(document.body);
        fireEvent.click(document.body);

        await waitFor(() => {
            expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
        });
    });

    it("returns focus to the trigger after closing", async () => {
        renderPopover();
        const trigger = screen.getByRole("button", { name: "Filter jobs" });
        fireEvent.click(trigger);
        await waitFor(() => expect(screen.getByRole("dialog")).toBeInTheDocument());

        fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });

        await waitFor(() => {
            expect(trigger).toHaveFocus();
        });
    });

    it("supports controlled open state via onOpenChange", () => {
        const onOpenChange = vi.fn();
        render(
            <Popover.Root open={false} onOpenChange={onOpenChange}>
                <Popover.Trigger>Filter jobs</Popover.Trigger>
                <Popover.Popup>
                    <p>Panel content</p>
                </Popover.Popup>
            </Popover.Root>,
        );

        fireEvent.click(screen.getByRole("button", { name: "Filter jobs" }));
        expect(onOpenChange).toHaveBeenCalledWith(true, expect.anything());
        // Still closed — the caller (via `open`) never flipped it to true.
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    it("renders an Arrow pointing back at the trigger when arrow is set", async () => {
        render(
            <Popover.Root open>
                <Popover.Trigger>Filter jobs</Popover.Trigger>
                <Popover.Popup arrow>
                    <p>Panel content</p>
                </Popover.Popup>
            </Popover.Root>,
        );

        await waitFor(() => {
            expect(document.querySelector('[data-side]')).toBeInTheDocument();
        });
    });

    it("renders a labelled Close button composed from IconButton", async () => {
        render(
            <Popover.Root open>
                <Popover.Trigger>Filter jobs</Popover.Trigger>
                <Popover.Popup>
                    <Popover.Close label="Close filters"/>
                </Popover.Popup>
            </Popover.Root>,
        );

        await waitFor(() => {
            expect(screen.getByRole("button", { name: "Close filters" })).toBeInTheDocument();
        });
    });
});
