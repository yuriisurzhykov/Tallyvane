import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Separator } from "./Separator";

describe("Separator", () => {
    it("defaults to a horizontal, accessible separator", () => {
        render(<Separator />);

        const separator = screen.getByRole("separator");
        expect(separator).toHaveAttribute("aria-orientation", "horizontal");
        expect(separator).toHaveClass("border-t", "border-border-subtle");
    });

    it("renders a vertical separator", () => {
        render(<Separator orientation="vertical" />);

        const separator = screen.getByRole("separator");
        expect(separator).toHaveAttribute("aria-orientation", "vertical");
        expect(separator).toHaveClass("border-l", "border-border-subtle");
    });

    it("removes the separator role from the accessibility tree when decorative", () => {
        render(<Separator decorative />);

        expect(screen.queryByRole("separator")).not.toBeInTheDocument();
    });

    it("appends a caller-supplied className", () => {
        render(<Separator className="my-stack" />);

        expect(screen.getByRole("separator")).toHaveClass("my-stack");
    });
});
