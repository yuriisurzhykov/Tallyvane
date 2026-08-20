import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Numeric } from "./Numeric";

describe("Numeric", () => {
    it("renders inside Text's numeric styling", () => {
        render(<Numeric>$185,000</Numeric>);
        expect(screen.getByText("$185,000")).toHaveClass("text-numeric");
    });

    it("right-aligns by default", () => {
        render(<Numeric>42</Numeric>);
        expect(screen.getByText("42")).toHaveClass("text-right");
    });

    it("right-aligns when align is explicitly set to right", () => {
        render(<Numeric align="right">42</Numeric>);
        expect(screen.getByText("42")).toHaveClass("text-right");
    });

    it("left-aligns when align is set to left", () => {
        render(<Numeric align="left">42</Numeric>);
        const element = screen.getByText("42");
        expect(element).toHaveClass("text-left");
        expect(element).not.toHaveClass("text-right");
    });

    it("merges a caller-provided className with its own classes", () => {
        render(<Numeric className="mt-stack">42</Numeric>);
        const element = screen.getByText("42");
        expect(element).toHaveClass("text-numeric");
        expect(element).toHaveClass("text-right");
        expect(element).toHaveClass("mt-stack");
    });
});
