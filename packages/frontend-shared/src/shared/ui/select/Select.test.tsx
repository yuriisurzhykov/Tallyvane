import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Select } from "./Select";

const WORK_MODES = ["Remote", "Hybrid", "On-site"];
/** `Select.Root`'s own `items` type is `{ label, value }[]` (or `Record`/`Group[]`), never a bare `string[]` — verified against `SelectRoot.d.ts`. */
const WORK_MODE_ITEMS = WORK_MODES.map((mode) => ({ label: mode, value: mode }));

function BasicSelect(props: {
    readonly defaultValue?: string;
    readonly value?: string;
    readonly onValueChange?: (value: string | null) => void
}) {
    return (
        <Select.Root items={ WORK_MODE_ITEMS } { ...props }>
            <Select.Label>Work mode</Select.Label>
            <Select.Trigger>
                <Select.Value placeholder="Select a work mode"/>
                <Select.Icon/>
            </Select.Trigger>
            <Select.Popup>
                { WORK_MODES.map((mode) => (
                    <Select.Item key={ mode } value={ mode }>
                        { mode }
                    </Select.Item>
                )) }
            </Select.Popup>
        </Select.Root>
    );
}

/**
 * jsdom's `fireEvent.click` defaults to `MouseEvent.detail: 0`, indistinguishable
 * from a real Enter/Space keyboard activation to code that branches on it —
 * `Menu.test.tsx`'s own documented finding, which applies identically here
 * since `Select.Trigger` is, underneath, the same kind of Base UI button.
 * Every "open via a real pointer click" test below passes `{ detail: 1 }`
 * for that reason.
 */
function openWithMouseClick(trigger: HTMLElement) {
    fireEvent.click(trigger, { detail: 1 });
}

/**
 * Selecting an option with a real mouse click is a two-event sequence in
 * every browser: `pointerdown` then `click`. Base UI's own `SelectItem`
 * reads that ordering directly — `onPointerDown` is what flips its internal
 * `allowMouseSelectionRef` to `true`, and `onClick` refuses to select at all
 * while that ref is still `false` (verified by reading `SelectItem.mjs`
 * directly after a synthetic `fireEvent.click` alone silently did nothing).
 * A bare `fireEvent.click`, with no preceding `pointerdown`, is therefore not
 * a faithful simulation of a mouse selection — the same class of gap
 * `Menu.test.tsx`'s own `detail: 0` vs `detail: 1` finding already
 * documents for this exact kind of component.
 */
function selectWithMouseClick(option: HTMLElement) {
    fireEvent.pointerDown(option);
    fireEvent.click(option);
}

describe("Select", () => {
    it("renders a labeled, real trigger button and starts closed", () => {
        render(<BasicSelect/>);

        const trigger = screen.getByRole("combobox", { name: "Work mode" });
        expect(trigger.tagName).toBe("BUTTON");
        expect(trigger).toHaveAttribute("aria-expanded", "false");
        expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
    });

    it("shows the placeholder until a value is chosen", () => {
        render(<BasicSelect/>);
        expect(screen.getByText("Select a work mode")).toBeInTheDocument();
    });

    it("opens on a pointer click, exposing its options by role and name", () => {
        render(<BasicSelect/>);
        openWithMouseClick(screen.getByRole("combobox", { name: "Work mode" }));

        expect(screen.getByRole("combobox", { name: "Work mode" })).toHaveAttribute("aria-expanded", "true");
        expect(screen.getByRole("listbox")).toBeInTheDocument();
        for (const mode of WORK_MODES) {
            expect(screen.getByRole("option", { name: mode })).toBeInTheDocument();
        }
    });

    it("selects an option on click, shows it as the trigger's value, and closes", () => {
        render(<BasicSelect/>);
        openWithMouseClick(screen.getByRole("combobox", { name: "Work mode" }));

        selectWithMouseClick(screen.getByRole("option", { name: "Hybrid" }));

        expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
        expect(screen.getByText("Hybrid")).toBeInTheDocument();
    });

    it("marks the selected option with aria-selected and renders its check indicator", () => {
        render(<BasicSelect defaultValue="Remote"/>);
        openWithMouseClick(screen.getByRole("combobox", { name: "Work mode" }));

        const selected = screen.getByRole("option", { name: "Remote" });
        expect(selected).toHaveAttribute("aria-selected", "true");
        expect(selected.querySelector("svg")).not.toBeNull();

        const notSelected = screen.getByRole("option", { name: "Hybrid" });
        expect(notSelected).toHaveAttribute("aria-selected", "false");
    });

    it("closes on Escape and returns focus to the trigger", async () => {
        render(<BasicSelect/>);
        const trigger = screen.getByRole("combobox", { name: "Work mode" });
        openWithMouseClick(trigger);
        const listbox = screen.getByRole("listbox");

        fireEvent.keyDown(listbox, { key: "Escape" });

        expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
        await waitFor(() => expect(trigger).toHaveFocus());
    });

    describe("controlled / uncontrolled", () => {
        it("uncontrolled: defaultValue seeds the initial value and selection updates internal state", () => {
            render(<BasicSelect defaultValue="On-site"/>);
            expect(screen.getByText("On-site")).toBeInTheDocument();

            openWithMouseClick(screen.getByRole("combobox", { name: "Work mode" }));
            selectWithMouseClick(screen.getByRole("option", { name: "Remote" }));
            expect(screen.getByText("Remote")).toBeInTheDocument();
        });

        it("controlled: value stays under the caller's control and onValueChange reports every attempt", () => {
            const onValueChange = vi.fn();
            render(<BasicSelect value="Remote" onValueChange={ onValueChange }/>);

            openWithMouseClick(screen.getByRole("combobox", { name: "Work mode" }));
            selectWithMouseClick(screen.getByRole("option", { name: "Hybrid" }));

            expect(onValueChange).toHaveBeenCalledWith("Hybrid", expect.anything());
            // Still "Remote" — the caller (via `value`) never applied the change.
            expect(screen.getByText("Remote")).toBeInTheDocument();
        });

        it("transitions cleanly from uncontrolled to a freshly-controlled instance", () => {
            function Controlled() {
                const [value, setValue] = useState("Remote");
                return <BasicSelect value={ value } onValueChange={ (next) => setValue(next ?? "") }/>;
            }

            render(<Controlled/>);

            openWithMouseClick(screen.getByRole("combobox", { name: "Work mode" }));
            selectWithMouseClick(screen.getByRole("option", { name: "On-site" }));
            expect(screen.getByText("On-site")).toBeInTheDocument();
        });
    });

    describe("keyboard reachability and navigation", () => {
        it("is a real, focusable button — the element native activation depends on", () => {
            render(<BasicSelect/>);
            const trigger = screen.getByRole("combobox", { name: "Work mode" });
            trigger.focus();

            expect(trigger.tagName).toBe("BUTTON");
            expect(document.activeElement).toBe(trigger);
        });

        it("ArrowDown moves the highlight through the list", () => {
            render(<BasicSelect defaultValue="Remote"/>);
            openWithMouseClick(screen.getByRole("combobox", { name: "Work mode" }));
            const listbox = screen.getByRole("listbox");

            fireEvent.keyDown(listbox, { key: "ArrowDown" });
            expect(screen.getByRole("option", { name: "Hybrid" })).toHaveAttribute("data-highlighted");
        });

        it("Home and End jump to the first and last option", () => {
            render(<BasicSelect defaultValue="Hybrid"/>);
            openWithMouseClick(screen.getByRole("combobox", { name: "Work mode" }));
            const listbox = screen.getByRole("listbox");

            fireEvent.keyDown(listbox, { key: "End" });
            expect(screen.getByRole("option", { name: "On-site" })).toHaveAttribute("data-highlighted");

            fireEvent.keyDown(listbox, { key: "Home" });
            expect(screen.getByRole("option", { name: "Remote" })).toHaveAttribute("data-highlighted");
        });

        it("typeahead jumps to the option whose label starts with the typed character", () => {
            render(<BasicSelect/>);
            openWithMouseClick(screen.getByRole("combobox", { name: "Work mode" }));
            const listbox = screen.getByRole("listbox");

            fireEvent.keyDown(listbox, { key: "h" });
            expect(screen.getByRole("option", { name: "Hybrid" })).toHaveAttribute("data-highlighted");
        });

        it("Enter selects the highlighted option", () => {
            render(<BasicSelect/>);
            openWithMouseClick(screen.getByRole("combobox", { name: "Work mode" }));
            const listbox = screen.getByRole("listbox");

            fireEvent.keyDown(listbox, { key: "ArrowDown" });
            const highlighted = screen.getByRole("option", { name: "Remote" });
            expect(highlighted).toHaveAttribute("data-highlighted");
            // Enter activates the currently (really, not just visually) focused option —
            // dispatched on that option itself, not the list container, since Base UI
            // registers its Enter handling per-item via real roving `tabindex`, verified
            // live after a keyDown on the list container alone left the list open.
            fireEvent.keyDown(highlighted, { key: "Enter" });

            expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
            expect(screen.getByText("Remote")).toBeInTheDocument();
        });
    });

    it("carries the focus-visible ring utility on the trigger, required of every interactive component in this system", () => {
        render(<BasicSelect/>);
        expect(screen.getByRole("combobox", { name: "Work mode" })).toHaveClass("focus-visible:focus-ring");
    });

    it("renders a semantic group with its label", () => {
        render(
            <Select.Root items={ [{ value: "Fruits", items: WORK_MODES }] }>
                <Select.Trigger>
                    <Select.Value placeholder="Pick one"/>
                    <Select.Icon/>
                </Select.Trigger>
                <Select.Popup>
                    <Select.Group>
                        <Select.GroupLabel>Fruits</Select.GroupLabel>
                        { WORK_MODES.map((mode) => (
                            <Select.Item key={ mode } value={ mode }>
                                { mode }
                            </Select.Item>
                        )) }
                    </Select.Group>
                </Select.Popup>
            </Select.Root>,
        );
        openWithMouseClick(screen.getByRole("combobox"));

        expect(screen.getByText("Fruits")).toBeInTheDocument();
        expect(screen.getByRole("group")).toBeInTheDocument();
    });

    it("renders a semantic separator between items", () => {
        render(
            <Select.Root>
                <Select.Trigger>
                    <Select.Value placeholder="Pick one"/>
                    <Select.Icon/>
                </Select.Trigger>
                <Select.Popup>
                    <Select.Item value="Remote">Remote</Select.Item>
                    <Select.Separator/>
                    <Select.Item value="Hybrid">Hybrid</Select.Item>
                </Select.Popup>
            </Select.Root>,
        );
        openWithMouseClick(screen.getByRole("combobox"));

        expect(screen.getByRole("separator")).toBeInTheDocument();
    });

    it.each(["sm", "md", "lg"] as const)("renders the %s control-height role on the trigger", (size) => {
        render(
            <Select.Root>
                <Select.Trigger size={ size }>
                    <Select.Value placeholder="Pick one"/>
                    <Select.Icon/>
                </Select.Trigger>
                <Select.Popup>
                    <Select.Item value="Remote">Remote</Select.Item>
                </Select.Popup>
            </Select.Root>,
        );

        expect(screen.getByRole("combobox")).toHaveClass(`h-(--control-height-${ size })`);
    });
});
