import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Stack, type SpacingRole } from "./Stack";

describe("Stack", () => {
    it("renders as a vertical flex container", () => {
        render(
            <Stack gap="stack">
                <p>Item</p>
            </Stack>,
        );

        expect(screen.getByText("Item").parentElement).toHaveClass("flex", "flex-col");
    });

    const gapCases: [SpacingRole, string][] = [
        ["inline-tight", "gap-inline-tight"],
        ["inline", "gap-inline"],
        ["stack-tight", "gap-stack-tight"],
        ["stack", "gap-stack"],
        ["group-gap", "gap-group-gap"],
        ["section-gap", "gap-section-gap"],
    ];

    it.each(gapCases)("applies the %s role as the %s class", (gap, expectedClass) => {
        render(
            <Stack gap={gap}>
                <p>Item</p>
            </Stack>,
        );

        expect(screen.getByText("Item").parentElement).toHaveClass(expectedClass);
    });

    it("renders its children", () => {
        render(
            <Stack gap="stack">
                <p>First</p>
                <p>Second</p>
            </Stack>,
        );

        expect(screen.getByText("First")).toBeInTheDocument();
        expect(screen.getByText("Second")).toBeInTheDocument();
    });

    it("appends a caller-supplied className", () => {
        render(
            <Stack gap="stack" className="w-full">
                <p>Item</p>
            </Stack>,
        );

        expect(screen.getByText("Item").parentElement).toHaveClass("w-full");
    });
});
