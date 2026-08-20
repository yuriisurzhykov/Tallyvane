import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Combobox } from "./Combobox";

const COMPANIES = ["Acme Corp", "Globex", "Initech", "Umbrella Corp"];

function BasicCombobox(props: {
    readonly defaultValue?: string;
    readonly value?: string | null;
    readonly onValueChange?: (value: string | null) => void
}) {
    return (
        <Combobox.Root items={ COMPANIES } { ...props }>
            <label htmlFor="company">Company</label>
            <Combobox.InputGroup>
                <Combobox.Input id="company" placeholder="Search companies"/>
                <Combobox.Clear label="Clear company"/>
                <Combobox.Trigger label="Show all companies"/>
            </Combobox.InputGroup>
            <Combobox.Popup>
                <Combobox.Empty>No companies found</Combobox.Empty>
                <Combobox.List>
                    { (company: string) => <Combobox.Item key={ company }
                                                          value={ company }>{ company }</Combobox.Item> }
                </Combobox.List>
            </Combobox.Popup>
        </Combobox.Root>
    );
}

function getInput() {
    return screen.getByRole("combobox", { name: "Company" });
}

/**
 * `ComboboxTrigger` opens on `mousedown` (`useClick(..., { event: 'mousedown' })`,
 * verified by reading `ComboboxTrigger.mjs` directly) — a bare `fireEvent.click`
 * alone does not open it. The actual open, in turn, is deferred one animation
 * frame (`floating-ui-react`'s own `useClick`: `frame.request(() =>
 * setOpenWithTouchDelay(...))`, read directly after a synchronous assertion
 * right after `fireEvent.mouseDown` found the list still closed) — so opening
 * via the trigger needs an `await waitFor(...)`, unlike opening via typing or
 * an arrow key, which apply synchronously through a different code path.
 */
async function openWithTrigger() {
    fireEvent.mouseDown(screen.getByRole("button", { name: "Show all companies" }));
    await waitFor(() => expect(screen.getByRole("listbox")).toBeInTheDocument());
}

/**
 * Real typing fires a native `input` event whose `InputEvent.inputType` is
 * `"insertText"`. `fireEvent.change` dispatches a plain `Event` with no such
 * property, which `ComboboxInput.mjs`'s own `onChange` explicitly treats as
 * "autofill-like" and therefore does *not* open the list on
 * (`autofillLikeInput = !inputType || inputType === 'insertReplacementText'`)
 * — verified by reading the source after a `fireEvent.change`-based filter
 * test found no options at all. `fireEvent.input` with an explicit
 * `inputType` is the faithful simulation.
 */
function typeInto(input: HTMLElement, value: string) {
    fireEvent.input(input, { target: { value }, inputType: "insertText" });
}

describe("Combobox", () => {
    it("renders a labeled input and named trigger/clear buttons", () => {
        render(<BasicCombobox/>);

        expect(getInput()).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Show all companies" })).toBeInTheDocument();
        // No value yet: Clear stays unmounted until there is something to clear.
        expect(screen.queryByRole("button", { name: "Clear company" })).not.toBeInTheDocument();
    });

    it("opens the list on the trigger and exposes every item by role and name", async () => {
        render(<BasicCombobox/>);
        await openWithTrigger();

        for (const company of COMPANIES) {
            expect(screen.getByRole("option", { name: company })).toBeInTheDocument();
        }
    });

    it("filters the list as the user types, using Base UI's own default match", () => {
        render(<BasicCombobox/>);
        typeInto(getInput(), "corp");

        expect(screen.getByRole("option", { name: "Acme Corp" })).toBeInTheDocument();
        expect(screen.getByRole("option", { name: "Umbrella Corp" })).toBeInTheDocument();
        expect(screen.queryByRole("option", { name: "Globex" })).not.toBeInTheDocument();
    });

    it("shows the Empty message when no item matches the filter", () => {
        render(<BasicCombobox/>);
        typeInto(getInput(), "nonexistent");

        // Base UI appends an invisible word-joiner to `Status`/`Empty` content so a screen
        // reader re-announces an unchanged message (verified live: the rendered text node is
        // "No companies found\u2060") — an exact string match would never see it.
        expect(screen.getByText(/No companies found/)).toBeInTheDocument();
        expect(screen.queryByRole("option")).not.toBeInTheDocument();
    });

    it("selects an item on click, shows it as the input's value, and closes", async () => {
        render(<BasicCombobox/>);
        await openWithTrigger();

        fireEvent.click(screen.getByRole("option", { name: "Globex" }));

        expect(getInput()).toHaveValue("Globex");
        expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
    });

    it("shows Clear once a value is set, and clearing empties the input", () => {
        render(<BasicCombobox defaultValue="Globex"/>);
        expect(getInput()).toHaveValue("Globex");

        const clear = screen.getByRole("button", { name: "Clear company" });
        fireEvent.click(clear);

        expect(getInput()).toHaveValue("");
    });

    it("closes on Escape and returns focus to the input", async () => {
        render(<BasicCombobox/>);
        const input = getInput();
        await openWithTrigger();

        fireEvent.keyDown(input, { key: "Escape" });

        await waitFor(() => expect(screen.queryByRole("listbox")).not.toBeInTheDocument());
        expect(input).toHaveFocus();
    });

    describe("controlled / uncontrolled", () => {
        it("uncontrolled: defaultValue seeds the initial value", () => {
            render(<BasicCombobox defaultValue="Initech"/>);
            expect(getInput()).toHaveValue("Initech");
        });

        it("controlled: value stays under the caller's control and onValueChange reports every attempt", async () => {
            const onValueChange = vi.fn();
            render(<BasicCombobox value="Initech" onValueChange={ onValueChange }/>);
            await openWithTrigger();

            fireEvent.click(screen.getByRole("option", { name: "Globex" }));

            expect(onValueChange).toHaveBeenCalledWith("Globex", expect.anything());
            // Still "Initech" — the caller (via `value`) never applied the change.
            expect(getInput()).toHaveValue("Initech");
        });

        it("transitions cleanly from uncontrolled to a freshly-controlled instance", async () => {
            function Controlled() {
                const [value, setValue] = useState<string | null>("Initech");
                return <BasicCombobox value={ value } onValueChange={ setValue }/>;
            }

            render(<Controlled/>);
            await openWithTrigger();

            fireEvent.click(screen.getByRole("option", { name: "Acme Corp" }));
            expect(getInput()).toHaveValue("Acme Corp");
        });
    });

    describe("keyboard reachability and navigation", () => {
        it("is a real, focusable textbox", () => {
            render(<BasicCombobox/>);
            const input = getInput();
            input.focus();

            expect(input.tagName).toBe("INPUT");
            expect(document.activeElement).toBe(input);
        });

        it("ArrowDown from the input opens the list and highlights the first item", () => {
            render(<BasicCombobox/>);
            const input = getInput();
            input.focus();

            fireEvent.keyDown(input, { key: "ArrowDown" });

            expect(screen.getByRole("listbox")).toBeInTheDocument();
            expect(screen.getByRole("option", { name: "Acme Corp" })).toHaveAttribute("data-highlighted");
        });

        it("ArrowDown again moves the highlight forward", () => {
            render(<BasicCombobox/>);
            const input = getInput();
            input.focus();

            fireEvent.keyDown(input, { key: "ArrowDown" });
            fireEvent.keyDown(input, { key: "ArrowDown" });

            expect(screen.getByRole("option", { name: "Globex" })).toHaveAttribute("data-highlighted");
        });

        it("Enter selects the highlighted item from the input", () => {
            render(<BasicCombobox/>);
            const input = getInput();
            input.focus();

            fireEvent.keyDown(input, { key: "ArrowDown" });
            fireEvent.keyDown(input, { key: "Enter" });

            expect(getInput()).toHaveValue("Acme Corp");
            expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
        });
    });

    it("carries the focus-visible ring utility on the input group, required of every interactive component in this system", () => {
        render(<BasicCombobox/>);
        const group = getInput().closest('[class*="focus-within:focus-ring"]');
        expect(group).not.toBeNull();
    });

    it("renders a semantic group with its label", async () => {
        render(
            <Combobox.Root>
                <label htmlFor="grouped-company">Company</label>
                <Combobox.InputGroup>
                    <Combobox.Input id="grouped-company" placeholder="Search"/>
                    <Combobox.Trigger label="Show all"/>
                </Combobox.InputGroup>
                <Combobox.Popup>
                    <Combobox.List>
                        <Combobox.Group>
                            <Combobox.GroupLabel>Recent</Combobox.GroupLabel>
                            { COMPANIES.map((company) => (
                                <Combobox.Item key={ company } value={ company }>
                                    { company }
                                </Combobox.Item>
                            )) }
                        </Combobox.Group>
                    </Combobox.List>
                </Combobox.Popup>
            </Combobox.Root>,
        );
        fireEvent.mouseDown(screen.getByRole("button", { name: "Show all" }));
        await waitFor(() => expect(screen.getByRole("group")).toBeInTheDocument());

        expect(screen.getByText("Recent")).toBeInTheDocument();
    });

    it.each(["sm", "md", "lg"] as const)("renders the %s control-height role on the InputGroup", (size) => {
        render(
            <Combobox.Root items={ COMPANIES }>
                <Combobox.InputGroup size={ size }>
                    <Combobox.Input placeholder="Search"/>
                    <Combobox.Trigger label="Show all"/>
                </Combobox.InputGroup>
                <Combobox.Popup>
                    <Combobox.List>
                        { (company: string) => <Combobox.Item key={ company }
                                                              value={ company }>{ company }</Combobox.Item> }
                    </Combobox.List>
                </Combobox.Popup>
            </Combobox.Root>,
        );

        expect(screen.getByRole("combobox").closest(`[class*="h-(--control-height-${ size })"]`)).not.toBeNull();
    });
});
