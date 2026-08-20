import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Surface } from "./Surface";

describe("Surface", () => {
    it("defaults to the primary variant's background class", () => {
        render(<Surface>Card</Surface>);

        expect(screen.getByText("Card")).toHaveClass("bg-surface-primary");
    });

    it("renders the elevated variant's background class", () => {
        render(<Surface variant="elevated">Card</Surface>);

        expect(screen.getByText("Card")).toHaveClass("bg-surface-elevated");
    });

    it("renders the inset variant's background class", () => {
        render(<Surface variant="inset">Card</Surface>);

        expect(screen.getByText("Card")).toHaveClass("bg-surface-inset");
    });

    it("always applies the border and radius roles, regardless of variant", () => {
        render(<Surface variant="elevated">Card</Surface>);

        expect(screen.getByText("Card")).toHaveClass("border", "border-border-subtle", "rounded-card");
    });

    it("never applies a shadow class", () => {
        render(<Surface>Card</Surface>);

        expect(screen.getByText("Card").className).not.toMatch(/shadow/);
    });

    it("appends a caller-supplied className alongside the role classes", () => {
        render(<Surface className="col-span-2">Card</Surface>);

        expect(screen.getByText("Card")).toHaveClass("bg-surface-primary", "col-span-2");
    });
});
