import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { VisuallyHidden } from "./VisuallyHidden";

describe("VisuallyHidden", () => {
    it("renders its children inside a span by default", () => {
        render(<VisuallyHidden>Screen-reader-only text</VisuallyHidden>);

        const element = screen.getByText("Screen-reader-only text");
        expect(element.tagName).toBe("SPAN");
        expect(element).toHaveClass("sr-only");
    });

    it("keeps the content in the accessibility tree rather than removing it", () => {
        render(<VisuallyHidden>Announced text</VisuallyHidden>);

        // `sr-only` clips visually but must never be `display: none` or
        // `visibility: hidden` — either would drop the node from the a11y
        // tree too, not just from the page's visible layout.
        const element = screen.getByText("Announced text");
        expect(element).not.toHaveStyle({ display: "none" });
        expect(element).not.toHaveStyle({ visibility: "hidden" });
    });

    it("renders through the render prop when the caller opts into a different element", () => {
        render(<VisuallyHidden render={<label />}>Field hint</VisuallyHidden>);

        const element = screen.getByText("Field hint");
        expect(element.tagName).toBe("LABEL");
        expect(element).toHaveClass("sr-only");
    });

    it("merges a caller-provided className with its own sr-only class", () => {
        render(<VisuallyHidden className="inline">Combined classes</VisuallyHidden>);

        const element = screen.getByText("Combined classes");
        expect(element).toHaveClass("sr-only");
        expect(element).toHaveClass("inline");
    });
});
