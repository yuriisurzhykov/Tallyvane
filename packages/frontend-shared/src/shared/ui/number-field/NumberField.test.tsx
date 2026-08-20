import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { NumberField } from "./NumberField";

function BasicNumberField(props: {
    readonly defaultValue?: number;
    readonly min?: number;
    readonly max?: number;
    readonly step?: number
}) {
    return (
        <NumberField.Root id="amount" { ...props }>
            <label htmlFor="amount">Amount</label>
            <NumberField.Group>
                <NumberField.Decrement label="Decrease amount"/>
                <NumberField.Input/>
                <NumberField.Increment label="Increase amount"/>
            </NumberField.Group>
        </NumberField.Root>
    );
}

function getInput() {
    return screen.getByRole("textbox", { name: "Amount" });
}

describe("NumberField", () => {
    it("renders a labeled input and named, real stepper buttons", () => {
        render(<BasicNumberField defaultValue={ 5 }/>);

        expect(getInput()).toHaveValue("5");
        const increment = screen.getByRole("button", { name: "Increase amount" });
        const decrement = screen.getByRole("button", { name: "Decrease amount" });
        expect(increment.tagName).toBe("BUTTON");
        expect(decrement.tagName).toBe("BUTTON");
    });

    it("renders lucide plus/minus glyphs by default", () => {
        const { container } = render(<BasicNumberField defaultValue={ 0 }/>);
        expect(container.querySelectorAll("svg")).toHaveLength(2);
    });

    it("accepts a custom glyph via children", () => {
        render(
            <NumberField.Root id="amount" defaultValue={ 0 }>
                <label htmlFor="amount">Amount</label>
                <NumberField.Group>
                    <NumberField.Decrement label="Decrease amount">
                        <span data-testid="custom-minus"/>
                    </NumberField.Decrement>
                    <NumberField.Input/>
                    <NumberField.Increment label="Increase amount"/>
                </NumberField.Group>
            </NumberField.Root>,
        );

        expect(screen.getByTestId("custom-minus")).toBeInTheDocument();
    });

    describe("uncontrolled", () => {
        it("increments and decrements by the default step of 1", () => {
            render(<BasicNumberField defaultValue={ 5 }/>);

            fireEvent.click(screen.getByRole("button", { name: "Increase amount" }));
            expect(getInput()).toHaveValue("6");

            fireEvent.click(screen.getByRole("button", { name: "Decrease amount" }));
            fireEvent.click(screen.getByRole("button", { name: "Decrease amount" }));
            expect(getInput()).toHaveValue("4");
        });

        it("honors a custom step", () => {
            render(<BasicNumberField defaultValue={ 0 } step={ 5 }/>);

            fireEvent.click(screen.getByRole("button", { name: "Increase amount" }));
            expect(getInput()).toHaveValue("5");
        });

        it("clamps to max and disables Increment at the boundary", () => {
            render(<BasicNumberField defaultValue={ 9 } max={ 10 }/>);

            fireEvent.click(screen.getByRole("button", { name: "Increase amount" }));
            expect(getInput()).toHaveValue("10");
            expect(screen.getByRole("button", { name: "Increase amount" })).toBeDisabled();
        });

        it("clamps to min and disables Decrement at the boundary", () => {
            render(<BasicNumberField defaultValue={ 1 } min={ 0 }/>);

            fireEvent.click(screen.getByRole("button", { name: "Decrease amount" }));
            expect(getInput()).toHaveValue("0");
            expect(screen.getByRole("button", { name: "Decrease amount" })).toBeDisabled();
        });
    });

    describe("controlled", () => {
        function ControlledNumberField() {
            const [value, setValue] = useState<number | null>(2);
            return (
                <NumberField.Root id="amount" value={ value } onValueChange={ setValue }>
                    <label htmlFor="amount">Amount</label>
                    <NumberField.Group>
                        <NumberField.Decrement label="Decrease amount"/>
                        <NumberField.Input/>
                        <NumberField.Increment label="Increase amount"/>
                    </NumberField.Group>
                </NumberField.Root>
            );
        }

        it("reflects external value changes and reports every change via onValueChange", () => {
            render(<ControlledNumberField/>);
            expect(getInput()).toHaveValue("2");

            fireEvent.click(screen.getByRole("button", { name: "Increase amount" }));
            expect(getInput()).toHaveValue("3");
        });

        it("does not advance when the caller ignores onValueChange (stays controlled)", () => {
            const onValueChange = vi.fn();
            render(
                <NumberField.Root id="amount" value={ 5 } onValueChange={ onValueChange }>
                    <label htmlFor="amount">Amount</label>
                    <NumberField.Group>
                        <NumberField.Decrement label="Decrease amount"/>
                        <NumberField.Input/>
                        <NumberField.Increment label="Increase amount"/>
                    </NumberField.Group>
                </NumberField.Root>,
            );

            fireEvent.click(screen.getByRole("button", { name: "Increase amount" }));
            expect(onValueChange).toHaveBeenCalledWith(6, expect.anything());
            // Still 5 — the caller (via `value`) never applied the change.
            expect(getInput()).toHaveValue("5");
        });
    });

    describe("keyboard behavior", () => {
        it("ArrowUp/ArrowDown step the value from the input", () => {
            render(<BasicNumberField defaultValue={ 5 }/>);
            const input = getInput();
            input.focus();

            fireEvent.keyDown(input, { key: "ArrowUp" });
            expect(input).toHaveValue("6");

            fireEvent.keyDown(input, { key: "ArrowDown" });
            fireEvent.keyDown(input, { key: "ArrowDown" });
            expect(input).toHaveValue("4");
        });

        it("Home jumps to min and End jumps to max, when defined", () => {
            render(<BasicNumberField defaultValue={ 5 } min={ 0 } max={ 20 }/>);
            const input = getInput();
            input.focus();

            fireEvent.keyDown(input, { key: "End" });
            expect(input).toHaveValue("20");

            fireEvent.keyDown(input, { key: "Home" });
            expect(input).toHaveValue("0");
        });

        it("typing digits sets the value directly", () => {
            render(<BasicNumberField defaultValue={ 0 }/>);
            const input = getInput();

            fireEvent.change(input, { target: { value: "42" } });
            expect(input).toHaveValue("42");
        });
    });

    describe("sizes", () => {
        it.each(["sm", "md", "lg"] as const)("renders the %s control-height role on Input", (size) => {
            render(
                <NumberField.Root id="amount" defaultValue={ 0 }>
                    <label htmlFor="amount">Amount</label>
                    <NumberField.Group size={ size }>
                        <NumberField.Decrement label="Decrease amount"/>
                        <NumberField.Input/>
                        <NumberField.Increment label="Increase amount"/>
                    </NumberField.Group>
                </NumberField.Root>,
            );

            expect(getInput()).toHaveClass(`h-(--control-height-${ size })`);
        });

        it("defaults to md with no Group ancestor size set", () => {
            render(<BasicNumberField defaultValue={ 0 }/>);
            expect(getInput()).toHaveClass("h-(--control-height-md)");
        });
    });

    it("carries the focus-visible ring utility on the input, required of every interactive component in this system", () => {
        render(<BasicNumberField defaultValue={ 0 }/>);
        expect(getInput()).toHaveClass("focus-visible:focus-ring");
    });

    it("renders a ScrubArea wrapping caller-supplied content", () => {
        render(
            <NumberField.Root id="amount" defaultValue={ 0 }>
                <NumberField.ScrubArea>
                    <label htmlFor="amount">Amount</label>
                </NumberField.ScrubArea>
                <NumberField.Group>
                    <NumberField.Decrement label="Decrease amount"/>
                    <NumberField.Input/>
                    <NumberField.Increment label="Increase amount"/>
                </NumberField.Group>
            </NumberField.Root>,
        );

        expect(screen.getByText("Amount")).toBeInTheDocument();
        expect(getInput()).toBeInTheDocument();
    });
});
