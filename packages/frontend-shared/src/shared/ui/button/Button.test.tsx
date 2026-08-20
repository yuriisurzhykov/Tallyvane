import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Button, type ButtonSize, type ButtonTone } from "./Button";

const TONE_CLASS: Record<ButtonTone, string> = {
    primary: "bg-interactive-primary",
    neutral: "border-border-default",
    ghost: "hover:bg-surface-row-hover",
    danger: "bg-status-danger",
};

const SIZE_CLASS: Record<ButtonSize, string> = {
    sm: "h-(--control-height-sm)",
    md: "h-(--control-height-md)",
    lg: "h-(--control-height-lg)",
};

describe("Button", () => {
    describe.each(Object.entries(TONE_CLASS))('tone "%s"', (tone, expectedClass) => {
        it(`renders with the "${expectedClass}" class`, () => {
            render(<Button tone={tone as ButtonTone}>Save</Button>);
            expect(screen.getByRole("button", { name: "Save" })).toHaveClass(expectedClass);
        });
    });

    describe.each(Object.entries(SIZE_CLASS))('size "%s"', (size, expectedClass) => {
        it(`renders with the "${expectedClass}" height class`, () => {
            render(
                <Button tone="primary" size={size as ButtonSize}>
                    Save
                </Button>,
            );
            expect(screen.getByRole("button", { name: "Save" })).toHaveClass(expectedClass);
        });
    });

    it("defaults to the medium size when none is given", () => {
        render(<Button tone="primary">Save</Button>);
        expect(screen.getByRole("button", { name: "Save" })).toHaveClass("h-(--control-height-md)");
    });

    it("is disabled and marked aria-busy while loading, and hides the leading icon behind the spinner", () => {
        render(
            <Button tone="primary" loading leadingIcon={<span data-testid="leading-icon" />}>
                Saving
            </Button>,
        );

        const button = screen.getByRole("button", { name: "Saving" });
        expect(button).toBeDisabled();
        expect(button).toHaveAttribute("aria-busy", "true");
        expect(screen.queryByTestId("leading-icon")).not.toBeInTheDocument();
    });

    it("is not aria-busy when not loading", () => {
        render(<Button tone="primary">Save</Button>);
        expect(screen.getByRole("button", { name: "Save" })).not.toHaveAttribute("aria-busy");
    });

    it("renders a leading and trailing icon when not loading", () => {
        render(
            <Button
                tone="primary"
                leadingIcon={<span data-testid="leading-icon" />}
                trailingIcon={<span data-testid="trailing-icon" />}
            >
                Save
            </Button>,
        );

        expect(screen.getByTestId("leading-icon")).toBeInTheDocument();
        expect(screen.getByTestId("trailing-icon")).toBeInTheDocument();
    });

    it("respects an explicit disabled prop independently of loading", () => {
        render(
            <Button tone="primary" disabled>
                Save
            </Button>,
        );
        expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
    });

    it("renders the target element when render swaps it for an anchor, and stays keyboard-activatable", () => {
        render(
            <Button tone="primary" render={<a href="/jobs" />} nativeButton={false}>
                View jobs
            </Button>,
        );

        const link = screen.getByRole("button", { name: "View jobs" });
        expect(link.tagName).toBe("A");
        expect(link).toHaveAttribute("href", "/jobs");
    });

    it("carries the focus-visible ring utility, required of every interactive component in this system", () => {
        render(<Button tone="primary">Save</Button>);
        expect(screen.getByRole("button", { name: "Save" })).toHaveClass("focus-visible:focus-ring");
    });

    it("merges a caller-provided className with its own classes", () => {
        render(
            <Button tone="primary" className="mt-stack">
                Save
            </Button>,
        );

        const button = screen.getByRole("button", { name: "Save" });
        expect(button).toHaveClass("bg-interactive-primary");
        expect(button).toHaveClass("mt-stack");
    });
});
