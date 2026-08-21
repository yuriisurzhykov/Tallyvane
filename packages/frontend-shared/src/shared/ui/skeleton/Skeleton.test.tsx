import { describe, expect, it } from "vitest";
import { render } from "@testing-library/react";
import { Skeleton } from "./Skeleton";

describe("Skeleton", () => {
    it("is aria-hidden, since it is a purely decorative placeholder", () => {
        const { container } = render(<Skeleton/>);
        const skeleton = container.querySelector("div");
        expect(skeleton).toHaveAttribute("aria-hidden", "true");
    });

    it("defaults to a full-width, single-line-height block", () => {
        const { container } = render(<Skeleton/>);
        const skeleton = container.querySelector("div") as HTMLElement;
        expect(skeleton).toHaveClass("w-full");
        expect(skeleton).toHaveClass("h-stack");
    });

    it("animates via a real CSS animation, not a theme-keyed Tailwind utility that would resolve to nothing", () => {
        const { container } = render(<Skeleton/>);
        const skeleton = container.querySelector("div") as HTMLElement;
        expect(skeleton.style.animation).toContain("skeleton-pulse");
        expect(skeleton.className).not.toMatch(/\banimate-/);
    });

    it("declares its own @keyframes rather than relying on a cleared Tailwind namespace", () => {
        const { container } = render(<Skeleton/>);
        expect(container.querySelector("style")?.textContent).toContain("@keyframes skeleton-pulse");
    });

    it("merges a caller-provided className with its own default sizing", () => {
        const { container } = render(<Skeleton
            className="h-(--control-height-lg) w-(--control-height-lg) rounded-pill"/>);
        const skeleton = container.querySelector("div") as HTMLElement;
        expect(skeleton).toHaveClass("bg-surface-inset");
        expect(skeleton).toHaveClass("rounded-pill");
    });

    it("merges a caller-provided style with its own animation, for a call site sizing it to real content", () => {
        const { container } = render(<Skeleton style={ { width: 240, height: 20 } }/>);
        const skeleton = container.querySelector("div") as HTMLElement;
        expect(skeleton.style.width).toBe("240px");
        expect(skeleton.style.height).toBe("20px");
        expect(skeleton.style.animation).toContain("skeleton-pulse");
    });
});
