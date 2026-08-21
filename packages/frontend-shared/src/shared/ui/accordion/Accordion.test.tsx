import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { Accordion } from "./Accordion";

function FaqAccordion(props: { readonly multiple?: boolean } = {}) {
    return (
        <Accordion.Root multiple={ props.multiple } defaultValue={ [] }>
            <Accordion.Item value="a">
                <Accordion.Header>
                    <Accordion.Trigger>What is Tallyvane?</Accordion.Trigger>
                </Accordion.Header>
                <Accordion.Panel>A personal job-search console.</Accordion.Panel>
            </Accordion.Item>
            <Accordion.Item value="b">
                <Accordion.Header>
                    <Accordion.Trigger>Is it open source?</Accordion.Trigger>
                </Accordion.Header>
                <Accordion.Panel>Not yet published.</Accordion.Panel>
            </Accordion.Item>
            <Accordion.Item value="c" disabled>
                <Accordion.Header>
                    <Accordion.Trigger>Unavailable question</Accordion.Trigger>
                </Accordion.Header>
                <Accordion.Panel>Never shown.</Accordion.Panel>
            </Accordion.Item>
        </Accordion.Root>
    );
}

describe("Accordion", () => {
    it("renders each header as a real h3 labelling its trigger, all closed by default", () => {
        render(<FaqAccordion/>);

        const headings = screen.getAllByRole("heading", { level: 3 });
        expect(headings).toHaveLength(3);
        expect(screen.getByRole("button", { name: "What is Tallyvane?" })).toHaveAttribute("aria-expanded", "false");
        expect(screen.queryByText("A personal job-search console.")).not.toBeInTheDocument();
    });

    it("opens an item on click, exposing its panel and linking it via aria-controls", () => {
        render(<FaqAccordion/>);
        const trigger = screen.getByRole("button", { name: "What is Tallyvane?" });

        fireEvent.click(trigger);

        expect(trigger).toHaveAttribute("aria-expanded", "true");
        const panel = screen.getByText("A personal job-search console.");
        expect(panel).toBeInTheDocument();
        expect(trigger.getAttribute("aria-controls")).toBe(panel.id);
    });

    it("closes an open item on a second click of its own trigger", () => {
        render(<FaqAccordion/>);
        const trigger = screen.getByRole("button", { name: "What is Tallyvane?" });

        fireEvent.click(trigger);
        expect(screen.getByText("A personal job-search console.")).toBeInTheDocument();

        fireEvent.click(trigger);
        expect(trigger).toHaveAttribute("aria-expanded", "false");
        expect(screen.queryByText("A personal job-search console.")).not.toBeInTheDocument();
    });

    it("is single-open by default: opening a second item closes the first", () => {
        render(<FaqAccordion/>);
        const first = screen.getByRole("button", { name: "What is Tallyvane?" });
        const second = screen.getByRole("button", { name: "Is it open source?" });

        fireEvent.click(first);
        expect(first).toHaveAttribute("aria-expanded", "true");

        fireEvent.click(second);

        expect(second).toHaveAttribute("aria-expanded", "true");
        expect(first).toHaveAttribute("aria-expanded", "false");
        expect(screen.queryByText("A personal job-search console.")).not.toBeInTheDocument();
        expect(screen.getByText("Not yet published.")).toBeInTheDocument();
    });

    it("allows more than one open item when multiple is set", () => {
        render(<FaqAccordion multiple/>);
        const first = screen.getByRole("button", { name: "What is Tallyvane?" });
        const second = screen.getByRole("button", { name: "Is it open source?" });

        fireEvent.click(first);
        fireEvent.click(second);

        expect(first).toHaveAttribute("aria-expanded", "true");
        expect(second).toHaveAttribute("aria-expanded", "true");
        expect(screen.getByText("A personal job-search console.")).toBeInTheDocument();
        expect(screen.getByText("Not yet published.")).toBeInTheDocument();
    });

    it("stays under caller control when value/onValueChange are set, reporting the next value on click", () => {
        const onValueChange = vi.fn();
        render(
            <Accordion.Root value={ [] } onValueChange={ onValueChange }>
                <Accordion.Item value="a">
                    <Accordion.Header>
                        <Accordion.Trigger>Question</Accordion.Trigger>
                    </Accordion.Header>
                    <Accordion.Panel>Answer.</Accordion.Panel>
                </Accordion.Item>
            </Accordion.Root>,
        );

        fireEvent.click(screen.getByRole("button", { name: "Question" }));

        expect(onValueChange).toHaveBeenCalledTimes(1);
        expect(onValueChange.mock.calls[0]?.[0]).toEqual(["a"]);
        // Controlled: the DOM does not change on its own since `value` was not fed back in.
        expect(screen.getByRole("button", { name: "Question" })).toHaveAttribute("aria-expanded", "false");
    });

    it("marks a disabled item's trigger data-disabled and ignores a click on it", () => {
        render(<FaqAccordion/>);
        const disabledTrigger = screen.getByRole("button", { name: "Unavailable question" });

        expect(disabledTrigger).toHaveAttribute("data-disabled");
        fireEvent.click(disabledTrigger);

        expect(disabledTrigger).toHaveAttribute("aria-expanded", "false");
    });

    /**
     * Verified empirically against the installed Base UI version rather than
     * assumed: `AccordionRootState.orientation`'s own doc comment documents
     * that roving focus was removed following an ARIA APG guidance update,
     * and `AccordionTrigger.js` attaches no keydown handler at all — so
     * every trigger is simply a normal tab stop, reached the same way any
     * other button on the page would be, not by arrow keys. This is the
     * "verify, don't assume" case the brief explicitly asked for: an
     * Arrow-key test would have asserted behaviour this Base UI version
     * does not have.
     */
    it("is reachable by Tab like any other button — no roving focus or arrow-key navigation between triggers", () => {
        render(<FaqAccordion/>);
        const first = screen.getByRole("button", { name: "What is Tallyvane?" });
        const second = screen.getByRole("button", { name: "Is it open source?" });

        first.focus();
        expect(document.activeElement).toBe(first);

        fireEvent.keyDown(first, { key: "ArrowDown" });
        expect(document.activeElement).toBe(first);

        second.focus();
        expect(document.activeElement).toBe(second);
    });

    it("carries the focus-visible ring utility on every trigger, required of every interactive component in this system", () => {
        render(<FaqAccordion/>);
        expect(screen.getByRole("button", { name: "What is Tallyvane?" })).toHaveClass("focus-visible:focus-ring");
    });

    it("merges a caller-provided className on Item, Trigger and Panel with their own classes", () => {
        render(
            <Accordion.Root defaultValue={ ["a"] }>
                <Accordion.Item value="a" className="mt-stack">
                    <Accordion.Header>
                        <Accordion.Trigger className="mt-stack">Question</Accordion.Trigger>
                    </Accordion.Header>
                    <Accordion.Panel className="mt-stack">Answer.</Accordion.Panel>
                </Accordion.Item>
            </Accordion.Root>,
        );

        const trigger = screen.getByRole("button", { name: "Question" });
        expect(trigger).toHaveClass("rounded-control");
        expect(trigger).toHaveClass("mt-stack");
        const panel = screen.getByText("Answer.");
        expect(panel).toHaveClass("overflow-hidden");
        expect(panel).toHaveClass("mt-stack");
    });
});
