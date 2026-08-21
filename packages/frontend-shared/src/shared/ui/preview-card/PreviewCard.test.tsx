import { describe, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { fireEvent } from "@testing-library/dom";
import { PreviewCard } from "./PreviewCard";

/**
 * Unlike `Tooltip.Trigger`, `PreviewCard.Trigger` configures a real open
 * delay (`delay: () => ({ open: ..., close: ... })`, `PreviewCardTrigger.js`)
 * rather than routing through Floating UI's "rest" mechanism — `restMs`
 * stays at its default of `0`, so `onMouseEnter` itself starts the open
 * timer directly. A plain `mouseenter` is enough here; see `Tooltip.test.tsx`
 * for the sibling case where it is not.
 */
function hover(element: Element): void {
    fireEvent.mouseEnter(element);
}

function unhover(element: Element): void {
    fireEvent.mouseLeave(element);
}

function renderPreviewCard() {
    return render(
        <PreviewCard.Root>
            <PreviewCard.Trigger href="/jobs/123">Senior Platform Engineer</PreviewCard.Trigger>
            <PreviewCard.Popup>
                <p>Acme Corp — Remote — $180k–$210k</p>
            </PreviewCard.Popup>
        </PreviewCard.Root>,
    );
}

describe("PreviewCard", () => {
    it("is closed until the trigger is hovered or focused", () => {
        renderPreviewCard();
        expect(screen.queryByText("Acme Corp — Remote — $180k–$210k")).not.toBeInTheDocument();
    });

    it("renders the trigger as a real anchor with the given href", () => {
        renderPreviewCard();
        const trigger = screen.getByRole("link", { name: "Senior Platform Engineer" });
        expect(trigger).toHaveAttribute("href", "/jobs/123");
    });

    it("opens after the default hover delay", async () => {
        renderPreviewCard();
        hover(screen.getByRole("link", { name: "Senior Platform Engineer" }));

        await waitFor(() => {
            expect(screen.getByText("Acme Corp — Remote — $180k–$210k")).toBeInTheDocument();
        });
    });

    /**
     * Verified gap, not an assumption: `PreviewCard`'s keyboard-focus open
     * shares Base UI's `useFocus` hook with `Tooltip`, which gates on
     * `:focus-visible` — hardcoded to never match in jsdom (confirmed
     * directly in `Tooltip.test.tsx`'s own investigation). Untestable here
     * for the same reason; would need a real browser to verify.
     */
    it.skip("opens on keyboard focus — needs a real browser; jsdom's :focus-visible never matches", () => undefined);

    it("closes on Escape", async () => {
        renderPreviewCard();
        const trigger = screen.getByRole("link", { name: "Senior Platform Engineer" });
        hover(trigger);
        await waitFor(() => expect(screen.getByText("Acme Corp — Remote — $180k–$210k")).toBeInTheDocument());

        fireEvent.keyDown(trigger, { key: "Escape" });

        await waitFor(() => {
            expect(screen.queryByText("Acme Corp — Remote — $180k–$210k")).not.toBeInTheDocument();
        });
    });

    it("closes on mouse leave", async () => {
        renderPreviewCard();
        const trigger = screen.getByRole("link", { name: "Senior Platform Engineer" });
        hover(trigger);
        await waitFor(() => expect(screen.getByText("Acme Corp — Remote — $180k–$210k")).toBeInTheDocument());

        unhover(trigger);

        await waitFor(() => {
            expect(screen.queryByText("Acme Corp — Remote — $180k–$210k")).not.toBeInTheDocument();
        });
    });

    it("renders an Arrow pointing back at the trigger when composed", async () => {
        render(
            <PreviewCard.Root open>
                <PreviewCard.Trigger href="/jobs/123">Senior Platform Engineer</PreviewCard.Trigger>
                <PreviewCard.Popup>
                    <PreviewCard.Arrow />
                    <p>Acme Corp</p>
                </PreviewCard.Popup>
            </PreviewCard.Root>,
        );

        await waitFor(() => {
            expect(document.querySelector("[data-side]")).toBeInTheDocument();
        });
    });
});
