import { describe, expect, it } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { Collapsible } from "./Collapsible";

function BasicCollapsible() {
    return (
        <Collapsible.Root>
            <Collapsible.Trigger>Show details</Collapsible.Trigger>
            <Collapsible.Panel>The hidden details.</Collapsible.Panel>
        </Collapsible.Root>
    );
}

describe("Collapsible", () => {
    it("starts closed by default (uncontrolled), hiding the panel from the accessibility tree", () => {
        render(<BasicCollapsible/>);

        const trigger = screen.getByRole("button", { name: "Show details" });
        expect(trigger).toHaveAttribute("aria-expanded", "false");
        expect(screen.queryByText("The hidden details.")).not.toBeInTheDocument();
    });

    it("opens on click, exposing the panel and linking it via aria-controls", () => {
        render(<BasicCollapsible/>);
        const trigger = screen.getByRole("button", { name: "Show details" });

        fireEvent.click(trigger);

        expect(trigger).toHaveAttribute("aria-expanded", "true");
        const panel = screen.getByText("The hidden details.");
        expect(panel).toBeInTheDocument();
        expect(trigger.getAttribute("aria-controls")).toBe(panel.id);
    });

    it("closes again on a second click (toggle)", () => {
        render(<BasicCollapsible/>);
        const trigger = screen.getByRole("button", { name: "Show details" });

        fireEvent.click(trigger);
        expect(screen.getByText("The hidden details.")).toBeInTheDocument();

        fireEvent.click(trigger);
        expect(trigger).toHaveAttribute("aria-expanded", "false");
        expect(screen.queryByText("The hidden details.")).not.toBeInTheDocument();
    });

    it("starts open when defaultOpen is set", () => {
        render(
            <Collapsible.Root defaultOpen>
                <Collapsible.Trigger>Show details</Collapsible.Trigger>
                <Collapsible.Panel>The hidden details.</Collapsible.Panel>
            </Collapsible.Root>,
        );

        expect(screen.getByRole("button", { name: "Show details" })).toHaveAttribute("aria-expanded", "true");
        expect(screen.getByText("The hidden details.")).toBeInTheDocument();
    });

    it("stays under caller control when open/onOpenChange are set, reporting the next value on click", () => {
        let observed: boolean | undefined;
        render(
            <Collapsible.Root
                open={ false }
                onOpenChange={ (next) => {
                    observed = next;
                } }
            >
                <Collapsible.Trigger>Show details</Collapsible.Trigger>
                <Collapsible.Panel>The hidden details.</Collapsible.Panel>
            </Collapsible.Root>,
        );

        fireEvent.click(screen.getByRole("button", { name: "Show details" }));

        expect(observed).toBe(true);
        // Controlled: the DOM does not change on its own since `open` was not fed back in.
        expect(screen.getByRole("button", { name: "Show details" })).toHaveAttribute("aria-expanded", "false");
    });

    it("marks the trigger data-disabled and ignores a click when disabled", () => {
        render(
            <Collapsible.Root disabled>
                <Collapsible.Trigger>Show details</Collapsible.Trigger>
                <Collapsible.Panel>The hidden details.</Collapsible.Panel>
            </Collapsible.Root>,
        );
        const trigger = screen.getByRole("button", { name: "Show details" });

        expect(trigger).toHaveAttribute("data-disabled");
        fireEvent.click(trigger);

        expect(trigger).toHaveAttribute("aria-expanded", "false");
    });

    it("marks the trigger data-panel-open once open, per CollapsibleTriggerDataAttributes", () => {
        render(<BasicCollapsible/>);
        const trigger = screen.getByRole("button", { name: "Show details" });

        fireEvent.click(trigger);

        expect(trigger).toHaveAttribute("data-panel-open");
    });

    it("carries the focus-visible ring utility on the trigger, required of every interactive component in this system", () => {
        render(<BasicCollapsible/>);
        expect(screen.getByRole("button", { name: "Show details" })).toHaveClass("focus-visible:focus-ring");
    });

    it("merges a caller-provided className on Trigger and Panel with their own classes", () => {
        render(
            <Collapsible.Root defaultOpen>
                <Collapsible.Trigger className="mt-stack">Show details</Collapsible.Trigger>
                <Collapsible.Panel className="mt-stack">The hidden details.</Collapsible.Panel>
            </Collapsible.Root>,
        );

        const trigger = screen.getByRole("button", { name: "Show details" });
        expect(trigger).toHaveClass("rounded-control");
        expect(trigger).toHaveClass("mt-stack");
        const panel = screen.getByText("The hidden details.");
        expect(panel).toHaveClass("overflow-hidden");
        expect(panel).toHaveClass("mt-stack");
    });
});
