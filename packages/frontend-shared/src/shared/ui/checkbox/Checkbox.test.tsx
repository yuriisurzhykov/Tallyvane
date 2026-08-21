import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { fireEvent } from "@testing-library/dom";
import { Checkbox } from "./Checkbox";

describe("Checkbox", () => {
    it("renders an unchecked checkbox by default", () => {
        render(<Checkbox aria-label="Remote only"/>);
        const checkbox = screen.getByRole("checkbox", { name: "Remote only" });

        expect(checkbox).not.toBeChecked();
        expect(checkbox).toHaveAttribute("data-unchecked");
    });

    it("supports an uncontrolled default state", () => {
        render(<Checkbox aria-label="Remote only" defaultChecked/>);
        expect(screen.getByRole("checkbox", { name: "Remote only" })).toBeChecked();
    });

    it("toggles on click", () => {
        render(<Checkbox aria-label="Remote only"/>);
        const checkbox = screen.getByRole("checkbox", { name: "Remote only" });

        fireEvent.click(checkbox);
        expect(checkbox).toBeChecked();

        fireEvent.click(checkbox);
        expect(checkbox).not.toBeChecked();
    });

    it("toggles on Space, the native checkbox activation key", () => {
        render(<Checkbox aria-label="Remote only"/>);
        const checkbox = screen.getByRole("checkbox", { name: "Remote only" });
        checkbox.focus();

        fireEvent.keyDown(checkbox, { key: " " });
        fireEvent.keyUp(checkbox, { key: " " });

        expect(checkbox).toBeChecked();
    });

    it("does not toggle on Enter — that key is left to submit an enclosing form", () => {
        render(<Checkbox aria-label="Remote only"/>);
        const checkbox = screen.getByRole("checkbox", { name: "Remote only" });
        checkbox.focus();

        fireEvent.keyDown(checkbox, { key: "Enter" });
        fireEvent.keyUp(checkbox, { key: "Enter" });

        expect(checkbox).not.toBeChecked();
    });

    it("supports a controlled state via checked/onCheckedChange", () => {
        const onCheckedChange = vi.fn();
        render(<Checkbox aria-label="Remote only" checked={ false } onCheckedChange={ onCheckedChange }/>);
        const checkbox = screen.getByRole("checkbox", { name: "Remote only" });

        fireEvent.click(checkbox);

        expect(onCheckedChange).toHaveBeenCalledWith(true, expect.anything());
        // Still unchecked — the caller (via `checked`) never flipped it to true.
        expect(checkbox).not.toBeChecked();
    });

    it("reports an indeterminate state via aria-checked=mixed and shows the dash glyph", () => {
        render(<Checkbox aria-label="Select all" indeterminate/>);
        const checkbox = screen.getByRole("checkbox", { name: "Select all" });

        expect(checkbox).toHaveAttribute("aria-checked", "mixed");
        expect(checkbox).toHaveAttribute("data-indeterminate");
    });

    it("does not toggle, and is marked data-disabled, when disabled", () => {
        render(<Checkbox aria-label="Remote only" disabled/>);
        const checkbox = screen.getByRole("checkbox", { name: "Remote only" });

        expect(checkbox).toHaveAttribute("data-disabled");
        fireEvent.click(checkbox);
        expect(checkbox).not.toBeChecked();
    });

    it("marks the box invalid via aria-invalid, matching Input's convention", () => {
        render(<Checkbox aria-label="Terms" aria-invalid="true"/>);
        expect(screen.getByRole("checkbox", { name: "Terms" })).toHaveClass("aria-invalid:border-status-danger");
    });
});
