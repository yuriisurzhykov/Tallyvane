import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { PercentField } from "./PercentField";

/**
 * `locale="en-US"` is explicit, not incidental — see this component's own
 * README (and `money-field/README.md`'s identical entry) for why: this
 * machine's own runtime locale is `ru-UA`, under which a percent renders
 * with a comma decimal separator and a leading space before `%`.
 */
function BasicPercentField(props: {
    readonly defaultValue?: number;
    readonly min?: number;
    readonly max?: number;
    readonly step?: number
}) {
    return (
        <PercentField.Root id="match-rate" locale="en-US" { ...props }>
            <label htmlFor="match-rate">401(k) match rate</label>
            <PercentField.Group>
                <PercentField.Decrement label="Decrease match rate"/>
                <PercentField.Input/>
                <PercentField.Increment label="Increase match rate"/>
            </PercentField.Group>
        </PercentField.Root>
    );
}

function getInput() {
    return screen.getByRole("textbox", { name: "401(k) match rate" });
}

describe("PercentField", () => {
    it("displays raw basis points formatted as a percentage", () => {
        render(<BasicPercentField defaultValue={ 250 }/>);
        expect(getInput()).toHaveValue("2.5%");
    });

    it("displays a whole-percent value with no trailing decimal", () => {
        render(<BasicPercentField defaultValue={ 500 }/>);
        expect(getInput()).toHaveValue("5%");
    });

    it("displays a value beyond 100% without clamping — no domain-specific max is baked in", () => {
        render(<BasicPercentField defaultValue={ 15000 }/>);
        expect(getInput()).toHaveValue("150%");
    });

    it("displays zero correctly", () => {
        render(<BasicPercentField defaultValue={ 0 }/>);
        expect(getInput()).toHaveValue("0%");
    });

    describe("the integer-basis-points contract — proven with a real rendered interaction, not assumed", () => {
        it("typing a plain percent number reports the exact equivalent integer basis points via onValueChange", () => {
            const onValueChange = vi.fn();
            render(
                <PercentField.Root id="match-rate" locale="en-US" value={ 0 } onValueChange={ onValueChange }>
                    <label htmlFor="match-rate">401(k) match rate</label>
                    <PercentField.Group>
                        <PercentField.Decrement label="Decrease match rate"/>
                        <PercentField.Input/>
                        <PercentField.Increment label="Increase match rate"/>
                    </PercentField.Group>
                </PercentField.Root>,
            );

            fireEvent.change(getInput(), { target: { value: "2.5" } });

            // Not 2500 or 25000 — the exact conversion this component's own README derives.
            expect(onValueChange).toHaveBeenCalledWith(250, expect.anything());
            expect(Number.isInteger(onValueChange.mock.calls[0]?.[0])).toBe(true);
        });

        it("a fractional-basis-point percent rounds to the nearest whole basis point", () => {
            const onValueChange = vi.fn();
            render(
                <PercentField.Root id="match-rate" locale="en-US" value={ 0 } onValueChange={ onValueChange }>
                    <label htmlFor="match-rate">401(k) match rate</label>
                    <PercentField.Group>
                        <PercentField.Decrement label="Decrease match rate"/>
                        <PercentField.Input/>
                        <PercentField.Increment label="Increase match rate"/>
                    </PercentField.Group>
                </PercentField.Root>,
            );

            fireEvent.change(getInput(), { target: { value: "12.345" } });

            expect(onValueChange).toHaveBeenCalledWith(1235, expect.anything());
        });

        it("typing a value over 100% reports basis points beyond 10000, uncapped", () => {
            const onValueChange = vi.fn();
            render(
                <PercentField.Root id="match-rate" locale="en-US" value={ 0 } onValueChange={ onValueChange }>
                    <label htmlFor="match-rate">401(k) match rate</label>
                    <PercentField.Group>
                        <PercentField.Decrement label="Decrease match rate"/>
                        <PercentField.Input/>
                        <PercentField.Increment label="Increase match rate"/>
                    </PercentField.Group>
                </PercentField.Root>,
            );

            fireEvent.change(getInput(), { target: { value: "150" } });

            expect(onValueChange).toHaveBeenCalledWith(15000, expect.anything());
        });

        it("a fully round-tripped controlled field displays what it was told to display", () => {
            function ControlledPercentField() {
                const [value, setValue] = useState<number | null>(250);
                return (
                    <PercentField.Root id="match-rate" locale="en-US" value={ value } onValueChange={ setValue }>
                        <label htmlFor="match-rate">401(k) match rate</label>
                        <PercentField.Group>
                            <PercentField.Decrement label="Decrease match rate"/>
                            <PercentField.Input/>
                            <PercentField.Increment label="Increase match rate"/>
                        </PercentField.Group>
                    </PercentField.Root>
                );
            }

            render(<ControlledPercentField/>);
            expect(getInput()).toHaveValue("2.5%");

            fireEvent.click(screen.getByRole("button", { name: "Increase match rate" }));
            // Default step is 100 basis points (one percentage point) — see README.
            expect(getInput()).toHaveValue("3.5%");
        });

        it("clearing the field reports null, not zero or NaN", () => {
            const onValueChange = vi.fn();
            render(
                <PercentField.Root id="match-rate" locale="en-US" value={ 250 } onValueChange={ onValueChange }>
                    <label htmlFor="match-rate">401(k) match rate</label>
                    <PercentField.Group>
                        <PercentField.Decrement label="Decrease match rate"/>
                        <PercentField.Input/>
                        <PercentField.Increment label="Increase match rate"/>
                    </PercentField.Group>
                </PercentField.Root>,
            );

            fireEvent.change(getInput(), { target: { value: "" } });

            expect(onValueChange).toHaveBeenCalledWith(null, expect.anything());
        });
    });

    describe("step, in basis points", () => {
        it("defaults to a 100-basis-point (one percentage point) step", () => {
            render(<BasicPercentField defaultValue={ 0 }/>);
            fireEvent.click(screen.getByRole("button", { name: "Increase match rate" }));
            expect(getInput()).toHaveValue("1%");
        });

        it("honors a custom basis-point step", () => {
            render(<BasicPercentField defaultValue={ 0 } step={ 25 }/>);
            fireEvent.click(screen.getByRole("button", { name: "Increase match rate" }));
            expect(getInput()).toHaveValue("0.25%");
        });
    });

    describe("min/max, in basis points — open-ended unless a call site sets them", () => {
        it("clamps to a call-site-supplied max and disables Increment at the boundary", () => {
            render(<BasicPercentField defaultValue={ 950 } max={ 1000 } step={ 100 }/>);
            fireEvent.click(screen.getByRole("button", { name: "Increase match rate" }));
            expect(getInput()).toHaveValue("10%");
            expect(screen.getByRole("button", { name: "Increase match rate" })).toBeDisabled();
        });

        it("clamps to a call-site-supplied min and disables Decrement at the boundary", () => {
            render(<BasicPercentField defaultValue={ 50 } min={ 0 } step={ 100 }/>);
            fireEvent.click(screen.getByRole("button", { name: "Decrease match rate" }));
            expect(getInput()).toHaveValue("0%");
            expect(screen.getByRole("button", { name: "Decrease match rate" })).toBeDisabled();
        });

        it("allows stepping past 10000 basis points (100%) when no max is given", () => {
            render(<BasicPercentField defaultValue={ 9900 } step={ 100 }/>);
            fireEvent.click(screen.getByRole("button", { name: "Increase match rate" }));
            expect(getInput()).toHaveValue("100%");
            expect(screen.getByRole("button", { name: "Increase match rate" })).not.toBeDisabled();
        });
    });

    describe("keyboard behavior", () => {
        it("ArrowUp/ArrowDown step by one percentage point (100 basis points) by default", () => {
            render(<BasicPercentField defaultValue={ 500 }/>);
            const input = getInput();
            input.focus();

            fireEvent.keyDown(input, { key: "ArrowUp" });
            expect(input).toHaveValue("6%");

            fireEvent.keyDown(input, { key: "ArrowDown" });
            fireEvent.keyDown(input, { key: "ArrowDown" });
            expect(input).toHaveValue("4%");
        });
    });

    it("carries the focus-visible ring utility on the input, required of every interactive component in this system", () => {
        render(<BasicPercentField defaultValue={ 0 }/>);
        expect(getInput()).toHaveClass("focus-visible:focus-ring");
    });
});
