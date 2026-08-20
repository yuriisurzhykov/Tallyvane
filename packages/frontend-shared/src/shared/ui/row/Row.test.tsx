import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Row, type SpacingRole } from "./Row";

describe("Row", () => {
    it("renders as a horizontal flex container, centred on the cross axis", () => {
        render(
            <Row gap="inline">
                <p>Item</p>
            </Row>,
        );

        expect(screen.getByText("Item").parentElement).toHaveClass("flex", "flex-row", "items-center");
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
            <Row gap={gap}>
                <p>Item</p>
            </Row>,
        );

        expect(screen.getByText("Item").parentElement).toHaveClass(expectedClass);
    });

    it("renders its children", () => {
        render(
            <Row gap="inline">
                <p>First</p>
                <p>Second</p>
            </Row>,
        );

        expect(screen.getByText("First")).toBeInTheDocument();
        expect(screen.getByText("Second")).toBeInTheDocument();
    });

    it("appends a caller-supplied className", () => {
        render(
            <Row gap="inline" className="w-full">
                <p>Item</p>
            </Row>,
        );

        expect(screen.getByText("Item").parentElement).toHaveClass("w-full");
    });
});
