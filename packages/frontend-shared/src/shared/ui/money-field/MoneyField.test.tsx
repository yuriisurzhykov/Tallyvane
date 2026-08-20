import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { MoneyField } from "./MoneyField";

/**
 * `locale="en-US"` is explicit, not incidental — this machine's own runtime
 * locale is `ru-UA` (verified directly: `new Intl.NumberFormat().resolvedOptions().locale`),
 * under which `$42.50` renders as `"42,50 $"`. See this component's own
 * README for the full story. Every test below pins the locale for exactly
 * this reason.
 */
function BasicMoneyField(props: {
    readonly defaultValue?: number;
    readonly min?: number;
    readonly max?: number;
    readonly step?: number
}) {
    return (
        <MoneyField.Root id="salary" locale="en-US" { ...props }>
            <label htmlFor="salary">Salary</label>
            <MoneyField.Group>
                <MoneyField.Decrement label="Decrease salary"/>
                <MoneyField.Input/>
                <MoneyField.Increment label="Increase salary"/>
            </MoneyField.Group>
        </MoneyField.Root>
    );
}

function getInput() {
    return screen.getByRole("textbox", { name: "Salary" });
}

describe("MoneyField", () => {
    it("displays a raw cents value formatted as USD currency", () => {
        render(<BasicMoneyField defaultValue={ 4250 }/>);
        expect(getInput()).toHaveValue("$42.50");
    });

    it("formats a value spanning a thousands separator", () => {
        render(<BasicMoneyField defaultValue={ 425000 }/>);
        expect(getInput()).toHaveValue("$4,250.00");
    });

    it("formats zero and negative cents correctly", () => {
        render(<BasicMoneyField defaultValue={ 0 }/>);
        expect(getInput()).toHaveValue("$0.00");
    });

    describe("the integer-cents contract — proven with a real rendered interaction, not assumed", () => {
        function ControlledMoneyField() {
            const [value, setValue] = useState<number | null>(0);
            return (
                <MoneyField.Root id="salary" locale="en-US" value={ value } onValueChange={ setValue }>
                    <label htmlFor="salary">Salary</label>
                    <MoneyField.Group>
                        <MoneyField.Decrement label="Decrease salary"/>
                        <MoneyField.Input/>
                        <MoneyField.Increment label="Increase salary"/>
                    </MoneyField.Group>
                </MoneyField.Root>
            );
        }

        it("typing a dollar-and-cents amount reports the exact equivalent integer cents via onValueChange", () => {
            const onValueChange = vi.fn();
            render(
                <MoneyField.Root id="salary" locale="en-US" value={ 0 } onValueChange={ onValueChange }>
                    <label htmlFor="salary">Salary</label>
                    <MoneyField.Group>
                        <MoneyField.Decrement label="Decrease salary"/>
                        <MoneyField.Input/>
                        <MoneyField.Increment label="Increase salary"/>
                    </MoneyField.Group>
                </MoneyField.Root>,
            );

            fireEvent.change(getInput(), { target: { value: "42.5" } });

            expect(onValueChange).toHaveBeenCalledWith(4250, expect.anything());
            // Never a float: a whole JS number, not "4250.000000000001" or similar.
            expect(Number.isInteger(onValueChange.mock.calls[0]?.[0])).toBe(true);
        });

        it("a sub-cent typed amount rounds to the nearest whole cent, not the raw float product", () => {
            const onValueChange = vi.fn();
            render(
                <MoneyField.Root id="salary" locale="en-US" value={ 0 } onValueChange={ onValueChange }>
                    <label htmlFor="salary">Salary</label>
                    <MoneyField.Group>
                        <MoneyField.Decrement label="Decrease salary"/>
                        <MoneyField.Input/>
                        <MoneyField.Increment label="Increase salary"/>
                    </MoneyField.Group>
                </MoneyField.Root>,
            );

            fireEvent.change(getInput(), { target: { value: "10.007" } });

            expect(onValueChange).toHaveBeenCalledWith(1001, expect.anything());
        });

        it("a fully round-tripped controlled field displays what it was told to display", () => {
            render(<ControlledMoneyField/>);
            expect(getInput()).toHaveValue("$0.00");

            fireEvent.click(screen.getByRole("button", { name: "Increase salary" }));
            // Default step is 100 cents (one dollar) — see README for why.
            expect(getInput()).toHaveValue("$1.00");
        });

        it("clearing the field reports null, not zero or NaN", () => {
            const onValueChange = vi.fn();
            render(
                <MoneyField.Root id="salary" locale="en-US" value={ 4250 } onValueChange={ onValueChange }>
                    <label htmlFor="salary">Salary</label>
                    <MoneyField.Group>
                        <MoneyField.Decrement label="Decrease salary"/>
                        <MoneyField.Input/>
                        <MoneyField.Increment label="Increase salary"/>
                    </MoneyField.Group>
                </MoneyField.Root>,
            );

            fireEvent.change(getInput(), { target: { value: "" } });

            expect(onValueChange).toHaveBeenCalledWith(null, expect.anything());
        });
    });

    describe("step, in cents", () => {
        it("defaults to a 100-cent (one dollar) step", () => {
            render(<BasicMoneyField defaultValue={ 0 }/>);
            fireEvent.click(screen.getByRole("button", { name: "Increase salary" }));
            expect(getInput()).toHaveValue("$1.00");
        });

        it("honors a custom cents step", () => {
            render(<BasicMoneyField defaultValue={ 0 } step={ 25 }/>);
            fireEvent.click(screen.getByRole("button", { name: "Increase salary" }));
            expect(getInput()).toHaveValue("$0.25");
        });
    });

    describe("min/max, in cents", () => {
        it("clamps to max and disables Increment at the boundary", () => {
            render(<BasicMoneyField defaultValue={ 950 } max={ 1000 } step={ 100 }/>);
            fireEvent.click(screen.getByRole("button", { name: "Increase salary" }));
            expect(getInput()).toHaveValue("$10.00");
            expect(screen.getByRole("button", { name: "Increase salary" })).toBeDisabled();
        });

        it("clamps to min and disables Decrement at the boundary", () => {
            render(<BasicMoneyField defaultValue={ 50 } min={ 0 } step={ 100 }/>);
            fireEvent.click(screen.getByRole("button", { name: "Decrease salary" }));
            expect(getInput()).toHaveValue("$0.00");
            expect(screen.getByRole("button", { name: "Decrease salary" })).toBeDisabled();
        });
    });

    describe("keyboard behavior", () => {
        it("ArrowUp/ArrowDown step by whole dollars (100 cents) by default", () => {
            render(<BasicMoneyField defaultValue={ 500 }/>);
            const input = getInput();
            input.focus();

            fireEvent.keyDown(input, { key: "ArrowUp" });
            expect(input).toHaveValue("$6.00");

            fireEvent.keyDown(input, { key: "ArrowDown" });
            fireEvent.keyDown(input, { key: "ArrowDown" });
            expect(input).toHaveValue("$4.00");
        });
    });

    it("supports a non-USD currency via the currency prop", () => {
        render(
            <MoneyField.Root id="fee" locale="en-US" currency="EUR" defaultValue={ 999 }>
                <label htmlFor="fee">Fee</label>
                <MoneyField.Group>
                    <MoneyField.Decrement label="Decrease fee"/>
                    <MoneyField.Input/>
                    <MoneyField.Increment label="Increase fee"/>
                </MoneyField.Group>
            </MoneyField.Root>,
        );
        expect(screen.getByRole("textbox", { name: "Fee" })).toHaveValue("€9.99");
    });

    it("carries the focus-visible ring utility on the input, required of every interactive component in this system", () => {
        render(<BasicMoneyField defaultValue={ 0 }/>);
        expect(getInput()).toHaveClass("focus-visible:focus-ring");
    });
});
