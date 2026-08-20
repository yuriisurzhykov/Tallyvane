import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import { ScrollArea } from "./ScrollArea";

/**
 * jsdom never computes real layout, so `scrollHeight`/`scrollWidth` are
 * always `0` — Base UI's own overflow detection (`clientHeight >=
 * scrollHeight`) then reads "no overflow" and unmounts the scrollbar
 * entirely (its documented, correct default). Stubbing a non-zero
 * `scrollHeight`/`scrollWidth` simulates real overflow so the scrollbar
 * actually mounts and its styling can be asserted on.
 */
let originalScrollHeight: PropertyDescriptor | undefined;
let originalScrollWidth: PropertyDescriptor | undefined;

beforeEach(() => {
    originalScrollHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "scrollHeight");
    originalScrollWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "scrollWidth");
    Object.defineProperty(HTMLElement.prototype, "scrollHeight", { configurable: true, value: 1000 });
    Object.defineProperty(HTMLElement.prototype, "scrollWidth", { configurable: true, value: 1000 });
});

afterEach(() => {
    if (originalScrollHeight) Object.defineProperty(HTMLElement.prototype, "scrollHeight", originalScrollHeight);
    if (originalScrollWidth) Object.defineProperty(HTMLElement.prototype, "scrollWidth", originalScrollWidth);
});

describe("ScrollArea", () => {
    it("renders children inside its scrollable viewport", () => {
        render(
            <ScrollArea>
                <p>Scrollable content</p>
            </ScrollArea>,
        );

        const viewport = screen.getByTestId("scroll-area-viewport");
        expect(within(viewport).getByText("Scrollable content")).toBeInTheDocument();
    });

    it("styles the track and thumb from existing surface/border roles rather than the browser default", async () => {
        const { container } = render(
            <ScrollArea>
                <p>Content</p>
            </ScrollArea>,
        );

        // Base UI recomputes overflow in a queued microtask after mount, not synchronously.
        await waitFor(() => {
            expect(container.querySelectorAll(".bg-surface-inset").length).toBeGreaterThan(0);
            expect(container.querySelectorAll(".bg-border-strong").length).toBeGreaterThan(0);
        });
    });

    it("appends a caller-supplied className to the root", () => {
        const { container } = render(
            <ScrollArea className="max-h-full">
                <p>Content</p>
            </ScrollArea>,
        );

        expect(container.firstElementChild).toHaveClass("max-h-full");
    });
});
