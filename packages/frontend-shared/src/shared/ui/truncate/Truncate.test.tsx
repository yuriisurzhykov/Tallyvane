import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Truncate } from "./Truncate";

describe("Truncate", () => {
    it("applies the line-clamp style for the default lines value", () => {
        render(<Truncate>Long text that should be clamped</Truncate>);
        const element = screen.getByText("Long text that should be clamped");
        expect(element.style.getPropertyValue("-webkit-line-clamp")).toBe("1");
        expect(element.style.getPropertyValue("display")).toBe("-webkit-box");
        expect(element.style.getPropertyValue("-webkit-box-orient")).toBe("vertical");
    });

    it("applies the line-clamp style for a given lines value", () => {
        render(<Truncate lines={3}>Long text that should be clamped</Truncate>);
        const element = screen.getByText("Long text that should be clamped");
        expect(element.style.getPropertyValue("-webkit-line-clamp")).toBe("3");
    });

    it("sets the title attribute when fullValue is passed", () => {
        render(<Truncate fullValue="The full, untruncated value">Clamped</Truncate>);
        expect(screen.getByText("Clamped")).toHaveAttribute("title", "The full, untruncated value");
    });

    it("omits the title attribute when fullValue is not passed", () => {
        render(<Truncate>Clamped</Truncate>);
        expect(screen.getByText("Clamped")).not.toHaveAttribute("title");
    });

    it("appends a caller-supplied className", () => {
        render(<Truncate className="mt-stack">Clamped</Truncate>);
        expect(screen.getByText("Clamped")).toHaveClass("overflow-hidden");
        expect(screen.getByText("Clamped")).toHaveClass("mt-stack");
    });
});
