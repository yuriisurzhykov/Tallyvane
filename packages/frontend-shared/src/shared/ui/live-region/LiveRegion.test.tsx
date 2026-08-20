import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { LiveRegion } from "./LiveRegion";

describe("LiveRegion", () => {
    it("defaults to polite, exposed as role=status with aria-live=polite", () => {
        render(<LiveRegion>12 results</LiveRegion>);
        const region = screen.getByRole("status");
        expect(region).toHaveAttribute("aria-live", "polite");
        expect(region).toHaveAttribute("aria-atomic", "true");
    });

    it("renders role=alert with aria-live=assertive when politeness is assertive", () => {
        render(<LiveRegion politeness="assertive">Save failed</LiveRegion>);
        const region = screen.getByRole("alert");
        expect(region).toHaveAttribute("aria-live", "assertive");
        expect(region).toHaveAttribute("aria-atomic", "true");
    });

    it("is visually hidden via the shared sr-only clipping technique, not display:none", () => {
        render(<LiveRegion>Background save finished</LiveRegion>);
        expect(screen.getByRole("status")).toHaveClass("sr-only");
    });

    it("renders its children as the announced text content", () => {
        render(<LiveRegion>3 jobs matched your filters</LiveRegion>);
        expect(screen.getByText("3 jobs matched your filters")).toBeInTheDocument();
    });

    it("renders a real div rather than VisuallyHidden's own default span, so role=status/alert is on the announcing element itself", () => {
        render(<LiveRegion>Announcement</LiveRegion>);
        expect(screen.getByRole("status").tagName).toBe("DIV");
    });

    it("re-renders new text in the same mounted element rather than requiring a remount to announce again", () => {
        const { rerender } = render(<LiveRegion>First message</LiveRegion>);
        const region = screen.getByRole("status");
        rerender(<LiveRegion>Second message</LiveRegion>);
        expect(region).toBe(screen.getByRole("status"));
        expect(screen.getByText("Second message")).toBeInTheDocument();
    });

    it("merges a caller-provided className with its own sr-only class", () => {
        render(<LiveRegion className="mt-stack">Announcement</LiveRegion>);
        const region = screen.getByRole("status");
        expect(region).toHaveClass("sr-only");
        expect(region).toHaveClass("mt-stack");
    });
});
