import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { fireEvent } from "@testing-library/dom";
import { Switch } from "./Switch";

describe("Switch", () => {
    it("renders off by default", () => {
        render(<Switch aria-label="Email reminders"/>);
        const toggle = screen.getByRole("switch", { name: "Email reminders" });

        expect(toggle).not.toBeChecked();
        expect(toggle).toHaveAttribute("data-unchecked");
    });

    it("supports an uncontrolled default state", () => {
        render(<Switch aria-label="Email reminders" defaultChecked/>);
        expect(screen.getByRole("switch", { name: "Email reminders" })).toBeChecked();
    });

    it("toggles on click", () => {
        render(<Switch aria-label="Email reminders"/>);
        const toggle = screen.getByRole("switch", { name: "Email reminders" });

        fireEvent.click(toggle);
        expect(toggle).toBeChecked();

        fireEvent.click(toggle);
        expect(toggle).not.toBeChecked();
    });

    it("toggles on Space", () => {
        render(<Switch aria-label="Email reminders"/>);
        const toggle = screen.getByRole("switch", { name: "Email reminders" });
        toggle.focus();

        fireEvent.keyDown(toggle, { key: " " });
        fireEvent.keyUp(toggle, { key: " " });

        expect(toggle).toBeChecked();
    });

    it("supports a controlled state via checked/onCheckedChange", () => {
        const onCheckedChange = vi.fn();
        render(<Switch aria-label="Email reminders" checked={ false } onCheckedChange={ onCheckedChange }/>);
        const toggle = screen.getByRole("switch", { name: "Email reminders" });

        fireEvent.click(toggle);

        expect(onCheckedChange).toHaveBeenCalledWith(true, expect.anything());
        expect(toggle).not.toBeChecked();
    });

    it("does not toggle, and is marked data-disabled, when disabled", () => {
        render(<Switch aria-label="Email reminders" disabled/>);
        const toggle = screen.getByRole("switch", { name: "Email reminders" });

        expect(toggle).toHaveAttribute("data-disabled");
        fireEvent.click(toggle);
        expect(toggle).not.toBeChecked();
    });

    it("slides the thumb via a translate-x driven by --switch-thumb-travel once checked", () => {
        render(<Switch aria-label="Email reminders" defaultChecked/>);
        const thumb = document.querySelector('[data-checked]:not([role="switch"])');

        expect(thumb).toHaveClass("data-[checked]:translate-x-(--switch-thumb-travel)");
        expect((thumb as HTMLElement).style.getPropertyValue("--switch-thumb-travel")).toBe("var(--ds-component-switch-thumb-travel)");
    });
});
