import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { fireEvent } from "@testing-library/dom";
import { RatingScale } from "./RatingScale";

const getValueLabel = (value: number) => `${ value } of 5`;

describe("RatingScale", () => {
    it("renders five dots by role and name", () => {
        render(<RatingScale label="Interest level" getValueLabel={ getValueLabel }/>);

        for (const value of [1, 2, 3, 4, 5]) {
            expect(screen.getByRole("radio", { name: `${ value } of 5` })).toBeInTheDocument();
        }
    });

    it("starts with nothing selected when no default is given", () => {
        render(<RatingScale label="Interest level" getValueLabel={ getValueLabel }/>);

        for (const value of [1, 2, 3, 4, 5]) {
            expect(screen.getByRole("radio", { name: `${ value } of 5` })).not.toBeChecked();
        }
    });

    it("supports an uncontrolled default rating, with the fill starting out cumulative too", () => {
        render(<RatingScale label="Interest level" getValueLabel={ getValueLabel } defaultValue={ 3 }/>);
        expect(screen.getByRole("radio", { name: "3 of 5" })).toBeChecked();

        for (const value of [1, 2, 3]) {
            expect(screen.getByRole("radio", { name: `${ value } of 5` })).toHaveClass("bg-interactive-primary");
        }
        for (const value of [4, 5]) {
            expect(screen.getByRole("radio", { name: `${ value } of 5` })).not.toHaveClass("bg-interactive-primary");
        }
    });

    it("checks (ARIA) exactly the clicked dot, but visually fills it and every dot before it", () => {
        render(<RatingScale label="Interest level" getValueLabel={ getValueLabel } defaultValue={ 2 }/>);

        fireEvent.click(screen.getByRole("radio", { name: "4 of 5" }));

        // Still exactly one radio is genuinely `checked` — a real ARIA radio
        // group can only ever have one, regardless of the visual fill below.
        expect(screen.getByRole("radio", { name: "4 of 5" })).toBeChecked();
        expect(screen.getByRole("radio", { name: "1 of 5" })).not.toBeChecked();
        expect(screen.getByRole("radio", { name: "2 of 5" })).not.toBeChecked();
        expect(screen.getByRole("radio", { name: "3 of 5" })).not.toBeChecked();
        expect(screen.getByRole("radio", { name: "5 of 5" })).not.toBeChecked();

        // The visual fill is cumulative, unlike `checked` — dots 1 through 4
        // read as selected, only 5 stays a plain ring.
        for (const value of [1, 2, 3, 4]) {
            expect(screen.getByRole("radio", { name: `${ value } of 5` })).toHaveClass("bg-interactive-primary");
        }
        expect(screen.getByRole("radio", { name: "5 of 5" })).not.toHaveClass("bg-interactive-primary");
    });

    it("supports a controlled rating via value/onValueChange, including starting unrated", () => {
        const onValueChange = vi.fn();
        render(<RatingScale label="Interest level" getValueLabel={ getValueLabel } value={ undefined }
                            onValueChange={ onValueChange }/>);

        fireEvent.click(screen.getByRole("radio", { name: "5 of 5" }));

        expect(onValueChange).toHaveBeenCalledWith(5, expect.anything());
        // Still unrated — the caller (via `value`) never flipped it, confirming a
        // controlled scale that starts `undefined` stays genuinely controlled
        // rather than silently locking into uncontrolled mode (see this
        // component's own comment on Base UI's `useControlled`).
        for (const value of [1, 2, 3, 4, 5]) {
            expect(screen.getByRole("radio", { name: `${ value } of 5` })).not.toBeChecked();
        }
    });

    it("reflects a controlled value update from the caller after starting unrated", () => {
        const onValueChange = vi.fn();
        const { rerender } = render(<RatingScale label="Interest level" getValueLabel={ getValueLabel }
                                                 value={ undefined } onValueChange={ onValueChange }/>);

        rerender(<RatingScale label="Interest level" getValueLabel={ getValueLabel } value={ 4 }
                              onValueChange={ onValueChange }/>);

        expect(screen.getByRole("radio", { name: "4 of 5" })).toBeChecked();
        for (const value of [1, 2, 3, 4]) {
            expect(screen.getByRole("radio", { name: `${ value } of 5` })).toHaveClass("bg-interactive-primary");
        }
        expect(screen.getByRole("radio", { name: "5 of 5" })).not.toHaveClass("bg-interactive-primary");
    });

    it("moves both focus and selection with the arrow keys, the same composite roving tabindex RadioGroup uses", async () => {
        render(<RatingScale label="Interest level" getValueLabel={ getValueLabel } defaultValue={ 2 }/>);
        const two = screen.getByRole("radio", { name: "2 of 5" });
        two.focus();

        fireEvent.keyDown(two, { key: "ArrowRight" });

        const three = screen.getByRole("radio", { name: "3 of 5" });
        await waitFor(() => expect(three).toHaveFocus());
        expect(three).toBeChecked();
    });

    it("does not select, and marks every dot data-disabled, when disabled", () => {
        render(<RatingScale label="Interest level" getValueLabel={ getValueLabel } disabled/>);
        const three = screen.getByRole("radio", { name: "3 of 5" });

        expect(three).toHaveAttribute("data-disabled");
        fireEvent.click(three);
        expect(three).not.toBeChecked();
    });
});
