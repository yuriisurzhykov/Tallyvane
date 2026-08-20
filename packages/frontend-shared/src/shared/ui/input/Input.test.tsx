import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Input, type InputSize } from "./Input";

describe("Input", () => {
    it.each<[InputSize, string]>([
        ["sm", "h-(--control-height-sm)"],
        ["md", "h-(--control-height-md)"],
        ["lg", "h-(--control-height-lg)"],
    ])("renders its height class for size %s", (size, heightClassName) => {
        render(<Input aria-label="Email" size={size} />);
        expect(screen.getByRole("textbox")).toHaveClass(heightClassName);
    });

    it("defaults to the medium height when no size is passed", () => {
        render(<Input aria-label="Email" />);
        expect(screen.getByRole("textbox")).toHaveClass("h-(--control-height-md)");
    });

    it("gets the danger-border treatment when marked aria-invalid", () => {
        render(<Input aria-label="Email" aria-invalid="true" />);
        const input = screen.getByRole("textbox");
        expect(input).toHaveAttribute("aria-invalid", "true");
        expect(input).toHaveClass("aria-invalid:border-status-danger");
    });

    it("does not carry the danger-border hook state when valid", () => {
        render(<Input aria-label="Email" />);
        expect(screen.getByRole("textbox")).not.toHaveAttribute("aria-invalid");
    });

    it("is visually distinct when disabled", () => {
        render(<Input aria-label="Email" disabled />);
        const input = screen.getByRole("textbox");
        expect(input).toBeDisabled();
        expect(input).toHaveAttribute("data-disabled");
        expect(input).toHaveClass("data-[disabled]:cursor-not-allowed");
        expect(input).toHaveClass("data-[disabled]:opacity-60");
    });

    it("is not disabled by default", () => {
        render(<Input aria-label="Email" />);
        expect(screen.getByRole("textbox")).not.toBeDisabled();
    });

    it("appends a caller-supplied className", () => {
        render(<Input aria-label="Email" className="mt-stack" />);
        expect(screen.getByRole("textbox")).toHaveClass("mt-stack");
        expect(screen.getByRole("textbox")).toHaveClass("bg-surface-inset");
    });

    it("forwards standard input attributes", () => {
        render(<Input aria-label="Email" type="email" placeholder="you@example.com" />);
        const input = screen.getByRole("textbox");
        expect(input).toHaveAttribute("type", "email");
        expect(input).toHaveAttribute("placeholder", "you@example.com");
    });
});
