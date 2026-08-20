import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { IconButton, type IconButtonSize, type IconButtonTone } from "./IconButton";

const TONE_CLASS: Record<IconButtonTone, string> = {
    primary: "bg-interactive-primary",
    neutral: "border-border-default",
    ghost: "hover:bg-surface-row-hover",
    danger: "bg-status-danger",
};

const SIZE_CLASS: Record<IconButtonSize, string> = {
    sm: "h-(--control-height-sm)",
    md: "h-(--control-height-md)",
    lg: "h-(--control-height-lg)",
};

describe("IconButton", () => {
    describe.each(Object.entries(TONE_CLASS))('tone "%s"', (tone, expectedClass) => {
        it(`renders with its "${expectedClass}" tone class`, () => {
            render(
                <IconButton label="Archive" tone={tone as IconButtonTone}>
                    <svg aria-hidden="true" />
                </IconButton>,
            );

            expect(screen.getByRole("button", { name: "Archive" })).toHaveClass(expectedClass);
        });
    });

    describe.each(Object.entries(SIZE_CLASS))('size "%s"', (size, expectedClass) => {
        it(`renders with its "${expectedClass}" square sizing class`, () => {
            render(
                <IconButton label="Archive" tone="neutral" size={size as IconButtonSize}>
                    <svg aria-hidden="true" />
                </IconButton>,
            );

            const button = screen.getByRole("button", { name: "Archive" });
            expect(button).toHaveClass(expectedClass);
            // Square: the same role drives both width and height.
            expect(button).toHaveClass(expectedClass.replace("h-", "w-"));
        });
    });

    it("defaults to the md size when none is given", () => {
        render(
            <IconButton label="Archive" tone="neutral">
                <svg aria-hidden="true" />
            </IconButton>,
        );

        const button = screen.getByRole("button", { name: "Archive" });
        expect(button).toHaveClass("h-(--control-height-md)");
        expect(button).toHaveClass("w-(--control-height-md)");
    });

    it("sets the accessible name from label via aria-label, queryable by role", () => {
        render(
            <IconButton label="Delete application" tone="danger">
                <svg aria-hidden="true" />
            </IconButton>,
        );

        const button = screen.getByRole("button", { name: "Delete application" });
        expect(button).toHaveAttribute("aria-label", "Delete application");
    });

    it("renders a real button element by default", () => {
        render(
            <IconButton label="Archive" tone="ghost">
                <svg aria-hidden="true" />
            </IconButton>,
        );

        expect(screen.getByRole("button", { name: "Archive" }).tagName).toBe("BUTTON");
    });

    it("defaults to type=\"button\" so it never submits an enclosing form", () => {
        render(
            <IconButton label="Toggle visibility" tone="ghost">
                <svg aria-hidden="true" />
            </IconButton>,
        );

        expect(screen.getByRole("button", { name: "Toggle visibility" })).toHaveAttribute("type", "button");
    });

    it("renders the target element when a render prop is supplied", () => {
        render(
            <IconButton label="View job posting" tone="neutral" render={<a href="/jobs/123" />}>
                <svg aria-hidden="true" />
            </IconButton>,
        );

        const link = screen.getByRole("link", { name: "View job posting" });
        expect(link.tagName).toBe("A");
        expect(link).toHaveAttribute("href", "/jobs/123");
    });

    it("merges a caller-provided className with its own tone and sizing classes", () => {
        render(
            <IconButton label="Archive" tone="primary" className="ml-inline-tight">
                <svg aria-hidden="true" />
            </IconButton>,
        );

        const button = screen.getByRole("button", { name: "Archive" });
        expect(button).toHaveClass("bg-interactive-primary");
        expect(button).toHaveClass("ml-inline-tight");
    });

    it("renders its children as the icon content", () => {
        render(
            <IconButton label="Archive" tone="neutral">
                <svg data-testid="icon" aria-hidden="true" />
            </IconButton>,
        );

        expect(screen.getByTestId("icon")).toBeInTheDocument();
    });
});
