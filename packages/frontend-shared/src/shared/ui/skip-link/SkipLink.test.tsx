import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { SkipLink } from "./SkipLink";

describe("SkipLink", () => {
    it("is visually hidden until focused, via Tailwind's sr-only utility", () => {
        render(<SkipLink href="#main-content">Skip to main content</SkipLink>);

        const link = screen.getByRole("link", { name: "Skip to main content" });
        expect(link).toHaveClass("sr-only");
    });

    it("carries the focus:not-sr-only variant that reveals it", () => {
        render(<SkipLink href="#main-content">Skip to main content</SkipLink>);

        expect(screen.getByRole("link", { name: "Skip to main content" })).toHaveClass("focus:not-sr-only");
    });

    it("points at whatever real landmark id the caller passes, not a hardcoded target", () => {
        render(<SkipLink href="#pipeline-table">Skip to pipeline</SkipLink>);

        expect(screen.getByRole("link", { name: "Skip to pipeline" })).toHaveAttribute("href", "#pipeline-table");
    });

    it("carries the focus ring utility, revealed on the same :focus that un-hides it", () => {
        render(<SkipLink href="#main-content">Skip to main content</SkipLink>);
        expect(screen.getByRole("link", { name: "Skip to main content" })).toHaveClass("focus:focus-ring");
    });

    it("keeps the content in the accessibility tree rather than removing it", () => {
        render(<SkipLink href="#main-content">Skip to main content</SkipLink>);

        const link = screen.getByRole("link", { name: "Skip to main content" });
        expect(link).not.toHaveStyle({ display: "none" });
        expect(link).not.toHaveStyle({ visibility: "hidden" });
    });
});
