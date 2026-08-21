import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { fireEvent } from "@testing-library/dom";
import { RadioGroup } from "../radio-group";
import { Radio } from "./Radio";

/** A bare `Radio` outside any group has no group context to derive `checked` from — Base UI falls back to `value === ""`, so every test renders inside a `RadioGroup` the same way a real call site always would. */
function SingleChoice(props: {
    readonly defaultValue?: string;
    readonly value?: string;
    readonly onValueChange?: (value: string) => void;
    readonly disabled?: boolean
}) {
    return (
        <RadioGroup aria-label="Work mode" { ...props }>
            <Radio aria-label="Remote" value="remote"/>
            <Radio aria-label="Hybrid" value="hybrid"/>
            <Radio aria-label="On-site" value="onsite"/>
        </RadioGroup>
    );
}

describe("Radio", () => {
    it("starts with none selected when no default is given", () => {
        render(<SingleChoice/>);

        expect(screen.getByRole("radio", { name: "Remote" })).not.toBeChecked();
        expect(screen.getByRole("radio", { name: "Hybrid" })).not.toBeChecked();
        expect(screen.getByRole("radio", { name: "On-site" })).not.toBeChecked();
    });

    it("supports an uncontrolled default selection", () => {
        render(<SingleChoice defaultValue="hybrid"/>);
        expect(screen.getByRole("radio", { name: "Hybrid" })).toBeChecked();
    });

    it("selects on click, and deselects the previous option", () => {
        render(<SingleChoice defaultValue="remote"/>);

        fireEvent.click(screen.getByRole("radio", { name: "Hybrid" }));

        expect(screen.getByRole("radio", { name: "Hybrid" })).toBeChecked();
        expect(screen.getByRole("radio", { name: "Remote" })).not.toBeChecked();
    });

    it("selects on Space", () => {
        render(<SingleChoice/>);
        const hybrid = screen.getByRole("radio", { name: "Hybrid" });
        hybrid.focus();

        fireEvent.keyDown(hybrid, { key: " " });
        fireEvent.keyUp(hybrid, { key: " " });

        expect(hybrid).toBeChecked();
    });

    it("does not select on Enter", () => {
        render(<SingleChoice/>);
        const hybrid = screen.getByRole("radio", { name: "Hybrid" });
        hybrid.focus();

        fireEvent.keyDown(hybrid, { key: "Enter" });
        fireEvent.keyUp(hybrid, { key: "Enter" });

        expect(hybrid).not.toBeChecked();
    });

    it("supports a controlled selection via value/onValueChange", () => {
        const onValueChange = vi.fn();
        render(<SingleChoice value="remote" onValueChange={ onValueChange }/>);

        fireEvent.click(screen.getByRole("radio", { name: "Hybrid" }));

        expect(onValueChange).toHaveBeenCalledWith("hybrid", expect.anything());
        // Still "remote" — the caller (via `value`) never flipped it.
        expect(screen.getByRole("radio", { name: "Remote" })).toBeChecked();
    });

    it("does not select, and is marked data-disabled, when the group is disabled", () => {
        render(<SingleChoice disabled/>);
        const hybrid = screen.getByRole("radio", { name: "Hybrid" });

        expect(hybrid).toHaveAttribute("data-disabled");
        fireEvent.click(hybrid);
        expect(hybrid).not.toBeChecked();
    });
});
