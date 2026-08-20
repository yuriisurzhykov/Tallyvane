import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Autocomplete } from "./Autocomplete";

const CITIES = ["Berlin", "Boston", "Bogotá", "Chicago", "Denver"];

function BasicAutocomplete(props: {
    readonly defaultValue?: string;
    readonly value?: string;
    readonly onValueChange?: (value: string) => void
}) {
    return (
        <Autocomplete.Root items={ CITIES } { ...props }>
            <label htmlFor="city">City</label>
            <Autocomplete.InputGroup>
                <Autocomplete.Input id="city" placeholder="Search cities"/>
                <Autocomplete.Clear label="Clear city"/>
                <Autocomplete.Trigger label="Show all cities"/>
            </Autocomplete.InputGroup>
            <Autocomplete.Popup>
                <Autocomplete.Empty>No cities found</Autocomplete.Empty>
                <Autocomplete.List>
                    { (city: string) => <Autocomplete.Item key={ city } value={ city }>{ city }</Autocomplete.Item> }
                </Autocomplete.List>
            </Autocomplete.Popup>
        </Autocomplete.Root>
    );
}

function getInput() {
    return screen.getByRole("combobox", { name: "City" });
}

/**
 * `AutocompleteTrigger` is, per Base UI's own source, literally
 * `ComboboxTrigger` reused — the same `useClick(..., { event: 'mousedown' })`
 * plus one-animation-frame-deferred open `Combobox.test.tsx`'s own
 * `openWithTrigger` helper already documents applies identically here.
 */
async function openWithTrigger() {
    fireEvent.mouseDown(screen.getByRole("button", { name: "Show all cities" }));
    await waitFor(() => expect(screen.getByRole("listbox")).toBeInTheDocument());
}

/** Real typing sets `InputEvent.inputType`; `fireEvent.change` does not — see `Combobox.test.tsx`'s own `typeInto` for the full finding, which applies identically to this component's shared `ComboboxInput`. */
function typeInto(input: HTMLElement, value: string) {
    fireEvent.input(input, { target: { value }, inputType: "insertText" });
}

describe("Autocomplete", () => {
    it("renders a labeled input and named trigger/clear buttons", () => {
        render(<BasicAutocomplete/>);

        expect(getInput()).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Show all cities" })).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Clear city" })).not.toBeInTheDocument();
    });

    it("opens the list on the trigger and exposes every item by role and name", async () => {
        render(<BasicAutocomplete/>);
        await openWithTrigger();

        for (const city of CITIES) {
            expect(screen.getByRole("option", { name: city })).toBeInTheDocument();
        }
    });

    it("filters suggestions as the user types", () => {
        render(<BasicAutocomplete/>);
        typeInto(getInput(), "bo");

        expect(screen.getByRole("option", { name: "Boston" })).toBeInTheDocument();
        expect(screen.getByRole("option", { name: "Bogotá" })).toBeInTheDocument();
        expect(screen.queryByRole("option", { name: "Berlin" })).not.toBeInTheDocument();
    });

    it("shows the Empty message when no suggestion matches", () => {
        render(<BasicAutocomplete/>);
        typeInto(getInput(), "zzz");

        expect(screen.getByText(/No cities found/)).toBeInTheDocument();
        expect(screen.queryByRole("option")).not.toBeInTheDocument();
    });

    it("accepts free text that matches no suggestion — the value need not be in the list", () => {
        render(<BasicAutocomplete/>);
        // Held as a direct reference, not re-queried by accessible name: opening the
        // popup marks the rest of the page `aria-hidden` (Floating UI's own
        // `markOthers`, the same finding `Combobox.test.tsx`'s own comment on this
        // documents), which includes this fixture's plain sibling `<label>` — a
        // native label is not part of Base UI's own tracked "labelable" set the way
        // `Select.Label`/`Combobox.Label` are, so the input's *computed accessible
        // name* is genuinely blank while the list is open, even though the element
        // itself is unaffected. See this component's own README.
        const input = getInput();
        typeInto(input, "Nowhereville");

        expect(input).toHaveValue("Nowhereville");
        expect(screen.getByText(/No cities found/)).toBeInTheDocument();
    });

    it("clicking a suggestion fills the input and closes the list", async () => {
        render(<BasicAutocomplete/>);
        await openWithTrigger();

        fireEvent.click(screen.getByRole("option", { name: "Denver" }));

        expect(getInput()).toHaveValue("Denver");
        expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
    });

    it("shows Clear once there is text, and clearing empties the input", () => {
        render(<BasicAutocomplete defaultValue="Denver"/>);
        expect(getInput()).toHaveValue("Denver");

        fireEvent.click(screen.getByRole("button", { name: "Clear city" }));
        expect(getInput()).toHaveValue("");
    });

    it("closes on Escape and returns focus to the input", async () => {
        render(<BasicAutocomplete/>);
        const input = getInput();
        await openWithTrigger();

        fireEvent.keyDown(input, { key: "Escape" });

        await waitFor(() => expect(screen.queryByRole("listbox")).not.toBeInTheDocument());
        expect(input).toHaveFocus();
    });

    describe("controlled / uncontrolled", () => {
        it("uncontrolled: defaultValue seeds the initial text", () => {
            render(<BasicAutocomplete defaultValue="Chicago"/>);
            expect(getInput()).toHaveValue("Chicago");
        });

        it("controlled: value stays under the caller's control and onValueChange reports every keystroke", () => {
            const onValueChange = vi.fn();
            render(<BasicAutocomplete value="Chicago" onValueChange={ onValueChange }/>);
            const input = getInput();

            typeInto(input, "Chicago Heights");

            expect(onValueChange).toHaveBeenCalledWith("Chicago Heights", expect.anything());
            // Still "Chicago" — the caller (via `value`) never applied the change.
            expect(input).toHaveValue("Chicago");
        });

        it("transitions cleanly from uncontrolled to a freshly-controlled instance", () => {
            function Controlled() {
                const [value, setValue] = useState("Chicago");
                return <BasicAutocomplete value={ value } onValueChange={ setValue }/>;
            }

            render(<Controlled/>);
            const input = getInput();

            // `mode="list"` (the default) filters suggestions against the *current*
            // input text, so opening the list while it still reads "Chicago" would
            // only ever show "Chicago" itself — clearing the query first is what
            // makes every city, "Denver" included, a candidate again.
            typeInto(input, "");
            fireEvent.keyDown(input, { key: "ArrowDown" });
            fireEvent.click(screen.getByRole("option", { name: "Denver" }));

            expect(input).toHaveValue("Denver");
        });
    });

    describe("keyboard reachability and navigation", () => {
        it("is a real, focusable textbox", () => {
            render(<BasicAutocomplete/>);
            const input = getInput();
            input.focus();

            expect(input.tagName).toBe("INPUT");
            expect(document.activeElement).toBe(input);
        });

        /**
         * Verified live, not assumed from `Combobox`: Autocomplete's default
         * (`selectionMode: 'none'`, `autoHighlight` unset) configuration sets
         * Floating UI's own `focusItemOnOpen` to `false` — read directly in
         * `AriaCombobox.mjs`: `focusItemOnOpen: queryChangedAfterOpen ||
         * (selectionMode === 'none' && !autoHighlightMode) ? false : 'auto'`.
         * A free-text field deliberately does not pre-highlight a suggestion
         * the moment the list opens, so as not to visually suggest a
         * "selected" value the user never chose — a real, considered
         * difference from `Combobox`, not a gap in this component's own
         * wrapper. The first `ArrowDown` therefore only opens the list; the
         * *second* moves the highlight to the first suggestion.
         */
        it("the first ArrowDown opens the list without pre-highlighting a suggestion", () => {
            render(<BasicAutocomplete/>);
            const input = getInput();
            input.focus();

            fireEvent.keyDown(input, { key: "ArrowDown" });

            expect(screen.getByRole("listbox")).toBeInTheDocument();
            for (const city of CITIES) {
                expect(screen.getByRole("option", { name: city })).not.toHaveAttribute("data-highlighted");
            }
        });

        it("a second ArrowDown highlights the first suggestion, and a third moves forward", () => {
            render(<BasicAutocomplete/>);
            const input = getInput();
            input.focus();

            fireEvent.keyDown(input, { key: "ArrowDown" });
            fireEvent.keyDown(input, { key: "ArrowDown" });
            expect(screen.getByRole("option", { name: "Berlin" })).toHaveAttribute("data-highlighted");

            fireEvent.keyDown(input, { key: "ArrowDown" });
            expect(screen.getByRole("option", { name: "Boston" })).toHaveAttribute("data-highlighted");
        });

        it("Enter selects the highlighted suggestion from the input", () => {
            render(<BasicAutocomplete/>);
            const input = getInput();
            input.focus();

            fireEvent.keyDown(input, { key: "ArrowDown" });
            fireEvent.keyDown(input, { key: "ArrowDown" });
            fireEvent.keyDown(input, { key: "Enter" });

            expect(getInput()).toHaveValue("Berlin");
            expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
        });
    });

    it("carries the focus-visible ring utility on the input group, required of every interactive component in this system", () => {
        render(<BasicAutocomplete/>);
        const group = getInput().closest('[class*="focus-within:focus-ring"]');
        expect(group).not.toBeNull();
    });

    it("renders a semantic group with its label", async () => {
        render(
            <Autocomplete.Root>
                <label htmlFor="grouped-city">City</label>
                <Autocomplete.InputGroup>
                    <Autocomplete.Input id="grouped-city" placeholder="Search"/>
                    <Autocomplete.Trigger label="Show all"/>
                </Autocomplete.InputGroup>
                <Autocomplete.Popup>
                    <Autocomplete.List>
                        <Autocomplete.Group>
                            <Autocomplete.GroupLabel>Recent</Autocomplete.GroupLabel>
                            { CITIES.map((city) => (
                                <Autocomplete.Item key={ city } value={ city }>
                                    { city }
                                </Autocomplete.Item>
                            )) }
                        </Autocomplete.Group>
                    </Autocomplete.List>
                </Autocomplete.Popup>
            </Autocomplete.Root>,
        );
        fireEvent.mouseDown(screen.getByRole("button", { name: "Show all" }));
        await waitFor(() => expect(screen.getByRole("group")).toBeInTheDocument());

        expect(screen.getByText("Recent")).toBeInTheDocument();
    });

    it.each(["sm", "md", "lg"] as const)("renders the %s control-height role on the InputGroup", (size) => {
        render(
            <Autocomplete.Root items={ CITIES }>
                <Autocomplete.InputGroup size={ size }>
                    <Autocomplete.Input placeholder="Search"/>
                    <Autocomplete.Trigger label="Show all"/>
                </Autocomplete.InputGroup>
                <Autocomplete.Popup>
                    <Autocomplete.List>
                        { (city: string) => <Autocomplete.Item key={ city }
                                                               value={ city }>{ city }</Autocomplete.Item> }
                    </Autocomplete.List>
                </Autocomplete.Popup>
            </Autocomplete.Root>,
        );

        expect(screen.getByRole("combobox").closest(`[class*="h-(--control-height-${ size })"]`)).not.toBeNull();
    });
});
