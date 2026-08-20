import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { fireEvent } from "@testing-library/dom";
import { CheckboxGroup } from "./CheckboxGroup";
import { Checkbox } from "../checkbox";

function TagFilters({ onValueChange, value }: {
    readonly onValueChange?: (value: string[]) => void;
    readonly value?: string[]
}) {
    return (
        <CheckboxGroup
            aria-label="Tech tags" { ...(value ? { value } : {}) } { ...(onValueChange ? { onValueChange } : {}) }>
            <Checkbox aria-label="React" value="react"/>
            <Checkbox aria-label="Kotlin" value="kotlin"/>
            <Checkbox aria-label="SQL" value="sql"/>
        </CheckboxGroup>
    );
}

describe("CheckboxGroup", () => {
    it("renders every child checkbox by role and name", () => {
        render(<TagFilters/>);

        expect(screen.getByRole("checkbox", { name: "React" })).toBeInTheDocument();
        expect(screen.getByRole("checkbox", { name: "Kotlin" })).toBeInTheDocument();
        expect(screen.getByRole("checkbox", { name: "SQL" })).toBeInTheDocument();
    });

    it("starts each child checked according to defaultValue", () => {
        render(
            <CheckboxGroup aria-label="Tech tags" defaultValue={ ["kotlin"] }>
                <Checkbox aria-label="React" value="react"/>
                <Checkbox aria-label="Kotlin" value="kotlin"/>
            </CheckboxGroup>,
        );

        expect(screen.getByRole("checkbox", { name: "React" })).not.toBeChecked();
        expect(screen.getByRole("checkbox", { name: "Kotlin" })).toBeChecked();
    });

    it("reports the updated set of ticked values when a child is clicked", () => {
        const onValueChange = vi.fn();
        render(<TagFilters value={ [] } onValueChange={ onValueChange }/>);

        fireEvent.click(screen.getByRole("checkbox", { name: "React" }));

        expect(onValueChange).toHaveBeenCalledWith(["react"], expect.anything());
    });

    it("supports a select-all parent checkbox that reports indeterminate when only some children are ticked", () => {
        const allValues = ["react", "kotlin"];

        function SelectAll() {
            return (
                <CheckboxGroup aria-label="Tech tags" defaultValue={ ["react"] } allValues={ allValues }>
                    <Checkbox aria-label="Select all" parent/>
                    <Checkbox aria-label="React" value="react"/>
                    <Checkbox aria-label="Kotlin" value="kotlin"/>
                </CheckboxGroup>
            );
        }

        render(<SelectAll/>);

        expect(screen.getByRole("checkbox", { name: "Select all" })).toHaveAttribute("aria-checked", "mixed");
    });

    it("checking the parent checkbox ticks every child", () => {
        const allValues = ["react", "kotlin"];

        function SelectAll() {
            return (
                <CheckboxGroup aria-label="Tech tags" defaultValue={ [] } allValues={ allValues }>
                    <Checkbox aria-label="Select all" parent/>
                    <Checkbox aria-label="React" value="react"/>
                    <Checkbox aria-label="Kotlin" value="kotlin"/>
                </CheckboxGroup>
            );
        }

        render(<SelectAll/>);
        fireEvent.click(screen.getByRole("checkbox", { name: "Select all" }));

        expect(screen.getByRole("checkbox", { name: "React" })).toBeChecked();
        expect(screen.getByRole("checkbox", { name: "Kotlin" })).toBeChecked();
    });
});
