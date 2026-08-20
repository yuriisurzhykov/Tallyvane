import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { Toggle } from "./Toggle";

describe("Toggle", () => {
    it("is unpressed by default (uncontrolled)", () => {
        render(<Toggle>Table view</Toggle>);

        const toggle = screen.getByRole("button", { name: "Table view" });
        expect(toggle).toHaveAttribute("aria-pressed", "false");
        expect(toggle).not.toHaveAttribute("data-pressed");
    });

    it("becomes pressed after a real click, uncontrolled", () => {
        render(<Toggle>Table view</Toggle>);
        const toggle = screen.getByRole("button", { name: "Table view" });

        fireEvent.click(toggle);

        expect(toggle).toHaveAttribute("aria-pressed", "true");
        expect(toggle).toHaveAttribute("data-pressed");
        expect(toggle).toHaveClass("data-[pressed]:bg-interactive-primary-subtle");
    });

    it("starts pressed when defaultPressed is set", () => {
        render(<Toggle defaultPressed>Table view</Toggle>);
        expect(screen.getByRole("button", { name: "Table view" })).toHaveAttribute("aria-pressed", "true");
    });

    it("stays under caller control when pressed/onPressedChange are set, reporting the next value on click", () => {
        const onPressedChange = vi.fn();
        render(
            <Toggle pressed onPressedChange={onPressedChange}>
                Table view
            </Toggle>,
        );
        const toggle = screen.getByRole("button", { name: "Table view" });

        fireEvent.click(toggle);

        expect(onPressedChange).toHaveBeenCalledTimes(1);
        expect(onPressedChange.mock.calls[0]?.[0]).toBe(false);
        // Controlled: the DOM state does not change on its own since the
        // caller did not feed the new value back in.
        expect(toggle).toHaveAttribute("aria-pressed", "true");
    });

    it("marks itself data-disabled and ignores a click when disabled", () => {
        const onPressedChange = vi.fn();
        render(
            <Toggle disabled onPressedChange={onPressedChange}>
                Table view
            </Toggle>,
        );
        const toggle = screen.getByRole("button", { name: "Table view" });

        fireEvent.click(toggle);

        expect(toggle).toHaveAttribute("data-disabled");
        expect(onPressedChange).not.toHaveBeenCalled();
    });

    it("carries the focus-visible ring utility, required of every interactive component in this system", () => {
        render(<Toggle>Table view</Toggle>);
        expect(screen.getByRole("button", { name: "Table view" })).toHaveClass("focus-visible:focus-ring");
    });

    it("merges a caller-provided className with its own classes", () => {
        render(<Toggle className="mt-stack">Table view</Toggle>);
        const toggle = screen.getByRole("button", { name: "Table view" });

        expect(toggle).toHaveClass("rounded-control");
        expect(toggle).toHaveClass("mt-stack");
    });
});
