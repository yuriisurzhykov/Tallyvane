import { describe, expect, it } from "vitest";
import { render } from "@testing-library/react";
import { Dot, type DotTone } from "./Dot";

const TONE_CLASS: Record<DotTone, string> = {
    neutral: "bg-text-muted",
    info: "bg-status-info-text",
    attention: "bg-status-attention-text",
    success: "bg-status-success-text",
    danger: "bg-status-danger-text",
};

describe("Dot", () => {
    describe.each(Object.entries(TONE_CLASS))('tone "%s"', (tone, expectedClass) => {
        it(`renders with the "${expectedClass}" background class, sized from the inline spacing role`, () => {
            const { container } = render(<Dot tone={tone as DotTone} />);
            const dot = container.firstChild as HTMLElement;

            expect(dot).toHaveClass(expectedClass);
            expect(dot).toHaveClass("size-inline");
            expect(dot).toHaveClass("rounded-pill");
        });
    });

    it("is aria-hidden when it carries no label, since it conveys no meaning on its own", () => {
        const { container } = render(<Dot tone="danger" />);
        const dot = container.firstChild as HTMLElement;

        expect(dot).toHaveAttribute("aria-hidden", "true");
    });

    it("is not aria-hidden when a label is provided, and exposes the label via VisuallyHidden", () => {
        const { container, getByText } = render(<Dot tone="danger" label="Overdue" />);
        const dot = container.firstChild as HTMLElement;

        expect(dot).not.toHaveAttribute("aria-hidden");
        const labelNode = getByText("Overdue");
        expect(labelNode.tagName).toBe("SPAN");
        expect(labelNode).toHaveClass("sr-only");
    });

    it("appends a caller-provided className for layout without dropping its own classes", () => {
        const { container } = render(<Dot tone="success" className="ml-inline-tight" />);
        const dot = container.firstChild as HTMLElement;

        expect(dot).toHaveClass("bg-status-success-text");
        expect(dot).toHaveClass("ml-inline-tight");
    });
});
