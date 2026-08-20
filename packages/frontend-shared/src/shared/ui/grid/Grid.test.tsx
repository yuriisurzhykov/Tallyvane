import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Grid, type SpacingRole } from "./Grid";

describe("Grid", () => {
    it("renders as a grid with the requested column count", () => {
        render(
            <Grid columns={3} gap="stack">
                <p>Cell</p>
            </Grid>,
        );

        const grid = screen.getByText("Cell").parentElement;
        expect(grid).toHaveClass("grid");
        expect(grid).toHaveStyle({ gridTemplateColumns: "repeat(3, minmax(0, 1fr))" });
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
            <Grid columns={2} gap={gap}>
                <p>Cell</p>
            </Grid>,
        );

        expect(screen.getByText("Cell").parentElement).toHaveClass(expectedClass);
    });

    it("renders its children", () => {
        render(
            <Grid columns={2} gap="stack">
                <p>First</p>
                <p>Second</p>
            </Grid>,
        );

        expect(screen.getByText("First")).toBeInTheDocument();
        expect(screen.getByText("Second")).toBeInTheDocument();
    });

    it("appends a caller-supplied className", () => {
        render(
            <Grid columns={2} gap="stack" className="p-screen-padding">
                <p>Cell</p>
            </Grid>,
        );

        expect(screen.getByText("Cell").parentElement).toHaveClass("p-screen-padding");
    });
});
