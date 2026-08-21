import { describe, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { fireEvent } from "@testing-library/dom";
import { Tooltip } from "./Tooltip";

/**
 * `TooltipTrigger.js` wires its default (no-provider) open delay through
 * Floating UI's "rest" mechanism (`restMs`), not a plain hover-then-wait:
 * `onMouseEnter` itself is a deliberate no-op whenever a rest-only delay is
 * configured (`isRestOnlyDelay`), and the timer that actually opens the
 * tooltip only starts from the *returned* `onMouseMove` handler once the
 * pointer has gone still over the trigger. A real mouse fires `mousemove`
 * continuously while hovering; jsdom does not, so the test fires one by
 * hand after `mouseenter` to start that rest timer.
 *
 * Real timers throughout this file, not fake ones: that timer fires from a
 * plain `setTimeout` outside any React event, so the resulting state update
 * only lands inside an `act()` boundary via `waitFor`'s own polling — a
 * faked clock advanced by hand skips that boundary and the assertion runs
 * before React has re-rendered.
 */
function hover(element: Element): void {
    fireEvent.mouseEnter(element);
    fireEvent.mouseMove(element);
}

function unhover(element: Element): void {
    fireEvent.mouseLeave(element);
}

function renderTooltip() {
    return render(
        <Tooltip.Root>
            <Tooltip.Trigger>Archive job</Tooltip.Trigger>
            <Tooltip.Popup>Archive this job application</Tooltip.Popup>
        </Tooltip.Root>,
    );
}

describe("Tooltip", () => {
    it("is closed until the trigger is hovered or focused", () => {
        renderTooltip();
        expect(screen.queryByText("Archive this job application")).not.toBeInTheDocument();
    });

    it("opens after the default hover delay", async () => {
        renderTooltip();
        hover(screen.getByText("Archive job"));

        await waitFor(() => {
            expect(screen.getByText("Archive this job application")).toBeInTheDocument();
        });
    });

    /**
     * Verified gap, not an assumption: Base UI's focus-open path gates on
     * `:focus-visible` (`useFocus.js`), which real browsers only match after
     * keyboard interaction — and jsdom hardcodes `Element.matches(":focus-visible")`
     * to always return `false` regardless of input modality (confirmed directly:
     * neither a plain `.focus()` nor a `keydown`-then-`.focus()` sequence ever
     * makes it match here). That makes "opens on keyboard focus" genuinely
     * untestable under jsdom, not merely inconvenient — it would need a real
     * browser, i.e. a Playwright spec, to verify. Flagged in this batch's
     * report rather than added speculatively, per the task's own guidance to
     * only add a dedicated Playwright spec for a real, found gap.
     */
    it.skip("opens on keyboard focus — needs a real browser; jsdom's :focus-visible never matches", () => undefined);

    it("closes on Escape", async () => {
        renderTooltip();
        const trigger = screen.getByText("Archive job");
        hover(trigger);
        await waitFor(() => expect(screen.getByText("Archive this job application")).toBeInTheDocument());

        fireEvent.keyDown(trigger, { key: "Escape" });

        await waitFor(() => {
            expect(screen.queryByText("Archive this job application")).not.toBeInTheDocument();
        });
    });

    it("closes on mouse leave", async () => {
        renderTooltip();
        const trigger = screen.getByText("Archive job");
        hover(trigger);
        await waitFor(() => expect(screen.getByText("Archive this job application")).toBeInTheDocument());

        unhover(trigger);

        await waitFor(() => {
            expect(screen.queryByText("Archive this job application")).not.toBeInTheDocument();
        });
    });

    it("never carries the same information through two conflicting tooltips at once", async () => {
        render(
            <Tooltip.Provider>
                <Tooltip.Root>
                    <Tooltip.Trigger>Edit</Tooltip.Trigger>
                    <Tooltip.Popup>Edit this row</Tooltip.Popup>
                </Tooltip.Root>
                <Tooltip.Root>
                    <Tooltip.Trigger>Archive</Tooltip.Trigger>
                    <Tooltip.Popup>Archive this row</Tooltip.Popup>
                </Tooltip.Root>
            </Tooltip.Provider>,
        );

        hover(screen.getByText("Edit"));
        await waitFor(() => expect(screen.getByText("Edit this row")).toBeInTheDocument());

        unhover(screen.getByText("Edit"));
        hover(screen.getByText("Archive"));

        await waitFor(() => {
            expect(screen.getByText("Archive this row")).toBeInTheDocument();
            expect(screen.queryByText("Edit this row")).not.toBeInTheDocument();
        });
    });
});
