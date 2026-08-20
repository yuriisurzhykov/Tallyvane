import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Spinner, type SpinnerSize } from "./Spinner";

const SIZE_VAR: Record<SpinnerSize, string> = {
    sm: "--control-height-sm",
    md: "--control-height-md",
    lg: "--control-height-lg",
};

describe("Spinner", () => {
    describe.each(Object.entries(SIZE_VAR))('size "%s"', (size, cssVar) => {
        it(`derives its diameter from ${ cssVar }`, () => {
            const { container } = render(<Spinner size={ size as SpinnerSize }/>);
            const spinner = container.querySelector("span") as HTMLElement;
            expect(spinner.style.width).toBe(`calc(var(${ cssVar }) / 2.5)`);
            expect(spinner.style.height).toBe(`calc(var(${ cssVar }) / 2.5)`);
        });
    });

    it("defaults to the md size when none is given", () => {
        const { container } = render(<Spinner/>);
        const spinner = container.querySelector("span") as HTMLElement;
        expect(spinner.style.width).toBe("calc(var(--control-height-md) / 2.5)");
    });

    it("is aria-hidden when it carries no label, since a decorative spinner conveys no meaning on its own", () => {
        const { container } = render(<Spinner/>);
        const spinner = container.querySelector("span") as HTMLElement;
        expect(spinner).toHaveAttribute("aria-hidden", "true");
    });

    it("exposes role=status and renders its label as real, visible text — not screen-reader-only", () => {
        render(<Spinner label="Rendering PDF"/>);
        const spinner = screen.getByRole("status");
        expect(spinner).not.toHaveAttribute("aria-hidden");
        // `role="status"` already announces visible text content on its own;
        // a real regression test for the bug this batch fixed, not just a
        // happy-path check — the label used to be wrapped in `VisuallyHidden`
        // (a real, shipped bug: a sighted user saw only a spinning ring with
        // no visible clue anything was loading).
        const labelNode = screen.getByText("Rendering PDF");
        expect(labelNode).not.toHaveClass("sr-only");
    });

    it("animates via a real CSS animation, not a theme-keyed Tailwind utility that would resolve to nothing", () => {
        const { container } = render(<Spinner/>);
        const spinner = container.querySelector("span") as HTMLElement;
        expect(spinner.style.animation).toContain("spinner-spin");
        expect(spinner.className).not.toMatch(/\banimate-/);
    });

    it("inherits its colour via border-current rather than choosing its own tone", () => {
        const { container } = render(<Spinner/>);
        const spinner = container.querySelector("span") as HTMLElement;
        expect(spinner).toHaveClass("border-current");
    });

    it("merges a caller-provided className with its own classes", () => {
        const { container } = render(<Spinner className="ml-inline-tight"/>);
        const spinner = container.querySelector("span") as HTMLElement;
        expect(spinner).toHaveClass("rounded-pill");
        expect(spinner).toHaveClass("ml-inline-tight");
    });
});
