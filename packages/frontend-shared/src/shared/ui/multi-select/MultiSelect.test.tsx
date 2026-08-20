import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MultiSelect } from "./MultiSelect";

const TAGS = ["React", "Vue", "Angular", "Svelte"];

function BasicMultiSelect(props: {
    readonly defaultValue?: string[];
    readonly value?: string[];
    readonly onValueChange?: (value: string[]) => void;
}) {
    return (
        <MultiSelect.Root items={ TAGS } { ...props }>
            <label htmlFor="tags">Tech tags</label>
            <MultiSelect.InputGroup>
                <MultiSelect.Chips>
                    <MultiSelect.Value>
                        { (value: string[]) =>
                            value.map((tag) => (
                                <MultiSelect.Chip key={ tag }>
                                    { tag }
                                    <MultiSelect.ChipRemove label={ `Remove ${ tag }` }/>
                                </MultiSelect.Chip>
                            ))
                        }
                    </MultiSelect.Value>
                    <MultiSelect.Input id="tags" placeholder="Add a tag"/>
                </MultiSelect.Chips>
                <MultiSelect.Trigger label="Show all tags"/>
            </MultiSelect.InputGroup>
            <MultiSelect.Popup>
                <MultiSelect.Empty>No tags found</MultiSelect.Empty>
                <MultiSelect.List>
                    { (tag: string) => (
                        <MultiSelect.Item key={ tag } value={ tag }>
                            { tag }
                        </MultiSelect.Item>
                    ) }
                </MultiSelect.List>
            </MultiSelect.Popup>
        </MultiSelect.Root>
    );
}

function getInput() {
    return screen.getByRole("combobox", { name: "Tech tags" });
}

/** Same gotcha `combobox/Combobox.test.tsx` already documents: the trigger opens on `mousedown`, one animation frame late. */
async function openWithTrigger() {
    fireEvent.mouseDown(screen.getByRole("button", { name: "Show all tags" }));
    await waitFor(() => expect(screen.getByRole("listbox")).toBeInTheDocument());
}

describe("MultiSelect", () => {
    it("renders a labeled combobox input with no chips by default", () => {
        render(<BasicMultiSelect/>);
        expect(getInput()).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: /^Remove / })).not.toBeInTheDocument();
    });

    it("renders one named, removable chip per existing value", () => {
        render(<BasicMultiSelect defaultValue={ ["React", "Vue"] }/>);

        expect(screen.getByText("React")).toBeInTheDocument();
        expect(screen.getByText("Vue")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Remove React" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Remove Vue" })).toBeInTheDocument();
    });

    describe("adding a value", () => {
        it("selecting an item from the popup adds a chip and reports the full array via onValueChange", async () => {
            const onValueChange = vi.fn();
            render(<BasicMultiSelect defaultValue={ [] } onValueChange={ onValueChange }/>);
            await openWithTrigger();

            fireEvent.click(screen.getByRole("option", { name: "React" }));

            expect(onValueChange).toHaveBeenCalledWith(["React"], expect.anything());
        });

        it("selecting a second item appends to the existing selection rather than replacing it", async () => {
            function Controlled() {
                const [value, setValue] = useState<string[]>(["React"]);
                return <BasicMultiSelect value={ value } onValueChange={ setValue }/>;
            }

            render(<Controlled/>);
            // Captured before the popup opens — `lessons-learned.mdc`'s own documented reason:
            // a plain sibling `<label>` is not exempt from Floating UI's `markOthers`, so the
            // input's *computed accessible name* goes blank for as long as the popup is open,
            // even though the input element itself is unaffected. Re-querying by name afterward
            // would fail for that reason alone.
            const input = getInput();
            await openWithTrigger();

            fireEvent.click(screen.getByRole("option", { name: "Vue" }));

            /**
             * Close the popup before querying chips by role — a real, verified Floating UI
             * behavior (`markOthers`), not a workaround for a bug: while the popup is open,
             * everything outside the tracked reference/floating pair — including this
             * component's own chips — is marked `aria-hidden="true"` (confirmed by rendering and
             * inspecting the real DOM directly). `Escape` while the popup is mounted closes it
             * without clearing the selection — the same safe pattern
             * `combobox/Combobox.test.tsx`'s own "closes on Escape" test already establishes.
             */
            fireEvent.keyDown(input, { key: "Escape" });
            await waitFor(() => expect(screen.queryByRole("listbox")).not.toBeInTheDocument());

            expect(screen.getByRole("button", { name: "Remove React" })).toBeInTheDocument();
            expect(screen.getByRole("button", { name: "Remove Vue" })).toBeInTheDocument();
        });
    });

    describe("removing a value", () => {
        it("clicking a chip's remove button removes exactly that value and keeps the others", () => {
            function Controlled() {
                const [value, setValue] = useState<string[]>(["React", "Vue", "Angular"]);
                return <BasicMultiSelect value={ value } onValueChange={ setValue }/>;
            }

            render(<Controlled/>);

            fireEvent.click(screen.getByRole("button", { name: "Remove Vue" }));

            expect(screen.getByText("React")).toBeInTheDocument();
            expect(screen.queryByText("Vue")).not.toBeInTheDocument();
            expect(screen.getByText("Angular")).toBeInTheDocument();
        });

        it("reports the array with exactly that value removed via onValueChange", () => {
            const onValueChange = vi.fn();
            render(<BasicMultiSelect value={ ["React", "Vue"] } onValueChange={ onValueChange }/>);

            fireEvent.click(screen.getByRole("button", { name: "Remove React" }));

            expect(onValueChange).toHaveBeenCalledWith(["Vue"], expect.anything());
        });

        it("Backspace in an empty input removes the last chip", () => {
            function Controlled() {
                const [value, setValue] = useState<string[]>(["React", "Vue"]);
                return <BasicMultiSelect value={ value } onValueChange={ setValue }/>;
            }

            render(<Controlled/>);
            const input = getInput();
            input.focus();

            fireEvent.keyDown(input, { key: "Backspace" });

            expect(screen.getByText("React")).toBeInTheDocument();
            expect(screen.queryByText("Vue")).not.toBeInTheDocument();
        });

        it("Backspace does nothing when the input already has text (does not eat a chip mid-typing)", () => {
            const onValueChange = vi.fn();
            render(<BasicMultiSelect value={ ["React"] } onValueChange={ onValueChange }/>);
            const input = getInput();
            fireEvent.input(input, { target: { value: "Sv" }, inputType: "insertText" });

            fireEvent.keyDown(input, { key: "Backspace" });

            expect(onValueChange).not.toHaveBeenCalled();
            expect(screen.getByText("React")).toBeInTheDocument();
        });
    });

    describe("controlled / uncontrolled", () => {
        it("uncontrolled: defaultValue seeds the initial chips", () => {
            render(<BasicMultiSelect defaultValue={ ["Angular"] }/>);
            expect(screen.getByText("Angular")).toBeInTheDocument();
        });

        it("controlled: value stays under the caller's control until it is explicitly applied", async () => {
            const onValueChange = vi.fn();
            render(<BasicMultiSelect value={ ["React"] } onValueChange={ onValueChange }/>);
            // Captured before the popup opens — see the identical comment above.
            const input = getInput();
            await openWithTrigger();

            fireEvent.click(screen.getByRole("option", { name: "Vue" }));

            expect(onValueChange).toHaveBeenCalledWith(["React", "Vue"], expect.anything());

            // Close the popup first — see the identical comment above for why (chips are
            // `aria-hidden` while the popup is open, verified against the real DOM).
            fireEvent.keyDown(input, { key: "Escape" });
            await waitFor(() => expect(screen.queryByRole("listbox")).not.toBeInTheDocument());

            // Still just "React" — the caller (via `value`) never applied the change.
            expect(screen.getByRole("button", { name: "Remove React" })).toBeInTheDocument();
            expect(screen.queryByRole("button", { name: "Remove Vue" })).not.toBeInTheDocument();
        });
    });

    describe("already-selected items in the popup — verified live, not assumed", () => {
        it("shows a checkmark next to an already-selected item, reusing Combobox.Item's own selection tracking", async () => {
            render(<BasicMultiSelect defaultValue={ ["Vue"] }/>);
            await openWithTrigger();

            expect(screen.getByRole("option", { name: "Vue" })).toHaveAttribute("aria-selected", "true");
            expect(screen.getByRole("option", { name: "React" })).toHaveAttribute("aria-selected", "false");
        });

        it("clicking an already-selected item's real, observed effect", async () => {
            const onValueChange = vi.fn();
            render(<BasicMultiSelect defaultValue={ ["Vue"] } onValueChange={ onValueChange }/>);
            await openWithTrigger();

            fireEvent.click(screen.getByRole("option", { name: "Vue" }));

            // Base UI's own multi-select toggles a re-clicked item back off, the standard
            // checkbox-list convention for a multi-select combobox — confirmed by running this
            // test, not assumed from the docs (which do not state this explicitly).
            expect(onValueChange).toHaveBeenCalledWith([], expect.anything());
        });
    });

    describe("keyboard reachability and chip navigation", () => {
        it("is a real, focusable textbox", () => {
            render(<BasicMultiSelect/>);
            const input = getInput();
            input.focus();

            expect(input.tagName).toBe("INPUT");
            expect(document.activeElement).toBe(input);
        });

        it("ArrowLeft with the caret at the start of an empty-ish input focuses the last chip", () => {
            render(<BasicMultiSelect defaultValue={ ["React", "Vue"] }/>);
            const input = getInput();
            input.focus();

            fireEvent.keyDown(input, { key: "ArrowLeft" });

            const lastChip = screen.getByText("Vue").closest('[tabindex="-1"]');
            expect(lastChip).toHaveFocus();
        });

        it("Backspace on the focused chip removes it and returns focus to the input", () => {
            function Controlled() {
                const [value, setValue] = useState<string[]>(["React", "Vue"]);
                return <BasicMultiSelect value={ value } onValueChange={ setValue }/>;
            }

            render(<Controlled/>);
            const input = getInput();
            input.focus();
            fireEvent.keyDown(input, { key: "ArrowLeft" });

            const focusedChip = document.activeElement;
            fireEvent.keyDown(focusedChip as HTMLElement, { key: "Backspace" });

            expect(screen.getByText("React")).toBeInTheDocument();
            expect(screen.queryByText("Vue")).not.toBeInTheDocument();
        });
    });

    it.each(["sm", "md", "lg"] as const)("renders the %s min-height role on the InputGroup", (size) => {
        render(
            <MultiSelect.Root items={ TAGS } defaultValue={ [] }>
                <MultiSelect.InputGroup size={ size }>
                    <MultiSelect.Chips>
                        <MultiSelect.Value>{ () => null }</MultiSelect.Value>
                        <MultiSelect.Input placeholder="Add a tag"/>
                    </MultiSelect.Chips>
                    <MultiSelect.Trigger label="Show all"/>
                </MultiSelect.InputGroup>
                <MultiSelect.Popup>
                    <MultiSelect.List>
                        { (tag: string) => (
                            <MultiSelect.Item key={ tag } value={ tag }>
                                { tag }
                            </MultiSelect.Item>
                        ) }
                    </MultiSelect.List>
                </MultiSelect.Popup>
            </MultiSelect.Root>,
        );

        expect(screen.getByRole("combobox").closest(`[class*="min-h-(--control-height-${ size })"]`)).not.toBeNull();
    });

    it("carries the focus-visible ring utility on a chip, which is a genuinely focusable element", () => {
        render(<BasicMultiSelect defaultValue={ ["React"] }/>);
        expect(screen.getByText("React").closest('[tabindex="-1"]')).toHaveClass("focus-visible:focus-ring");
    });

    it("carries the focus-visible ring utility on a chip's remove button", () => {
        render(<BasicMultiSelect defaultValue={ ["React"] }/>);
        expect(screen.getByRole("button", { name: "Remove React" })).toHaveClass("focus-visible:focus-ring");
    });

    it("renders a real, named dismiss button for each chip, with a lucide X glyph by default", () => {
        const { container } = render(<BasicMultiSelect defaultValue={ ["React", "Vue"] }/>);
        // `.lucide-x` specifically — `Trigger`'s own chevron glyph is a third `<svg>` on the page.
        expect(container.querySelectorAll("svg.lucide-x")).toHaveLength(2);
    });
});
