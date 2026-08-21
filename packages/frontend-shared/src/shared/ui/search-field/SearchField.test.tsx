import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { SearchField, type SearchFieldProps } from "./SearchField";
import { Field } from "../field";

/** A real controlled call site: holds `value` itself, exactly like every known consumer in `COMPONENTS.md` (`SearchableList`, `filter-pipeline`, `global-search`) would. */
function ControlledSearchField(props: Omit<SearchFieldProps, "value" | "onChange"> & { readonly initialValue?: string }) {
    const { initialValue = "", ...rest } = props;
    const [value, setValue] = useState(initialValue);
    return <SearchField {...rest} value={value} onChange={(event) => { setValue(event.target.value); }} />;
}

describe("SearchField", () => {
    beforeEach(() => {
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it("renders as a searchbox with the given value", () => {
        render(<ControlledSearchField aria-label="Search" initialValue="senior" onSearch={vi.fn()} clearLabel="Clear search" />);
        expect(screen.getByRole("searchbox", { name: "Search" })).toHaveValue("senior");
    });

    it("updates the visible value on every keystroke, without waiting for the debounce", () => {
        render(<ControlledSearchField aria-label="Search" onSearch={vi.fn()} clearLabel="Clear search" />);

        const input = screen.getByRole("searchbox", { name: "Search" });
        fireEvent.change(input, { target: { value: "eng" } });

        expect(input).toHaveValue("eng");
    });

    it("fires onSearch once, with the settled value, after debounceMs of no further typing", () => {
        const onSearch = vi.fn();
        render(<ControlledSearchField aria-label="Search" onSearch={onSearch} debounceMs={300} clearLabel="Clear search" />);
        const input = screen.getByRole("searchbox", { name: "Search" });

        fireEvent.change(input, { target: { value: "e" } });
        vi.advanceTimersByTime(100);
        fireEvent.change(input, { target: { value: "en" } });
        vi.advanceTimersByTime(100);
        fireEvent.change(input, { target: { value: "eng" } });

        expect(onSearch).not.toHaveBeenCalled();

        vi.advanceTimersByTime(299);
        expect(onSearch).not.toHaveBeenCalled();

        vi.advanceTimersByTime(1);
        expect(onSearch).toHaveBeenCalledTimes(1);
        expect(onSearch).toHaveBeenCalledWith("eng");
    });

    it("uses a default debounce of 300ms when debounceMs is not given", () => {
        const onSearch = vi.fn();
        render(<ControlledSearchField aria-label="Search" onSearch={onSearch} clearLabel="Clear search" />);

        fireEvent.change(screen.getByRole("searchbox", { name: "Search" }), { target: { value: "product" } });
        vi.advanceTimersByTime(299);
        expect(onSearch).not.toHaveBeenCalled();

        vi.advanceTimersByTime(1);
        expect(onSearch).toHaveBeenCalledWith("product");
    });

    it("does not render a clear button while the field is empty", () => {
        render(<ControlledSearchField aria-label="Search" onSearch={vi.fn()} clearLabel="Clear search" />);
        expect(screen.queryByRole("button", { name: "Clear search" })).not.toBeInTheDocument();
    });

    it("renders an enabled, keyboard-reachable clear button once there is text", () => {
        render(<ControlledSearchField aria-label="Search" initialValue="eng" onSearch={vi.fn()} clearLabel="Clear search" />);

        const clearButton = screen.getByRole("button", { name: "Clear search" });
        expect(clearButton).toBeEnabled();
        expect(clearButton).toHaveAttribute("type", "button");
        expect(clearButton).not.toHaveAttribute("tabindex", "-1");
    });

    it("clearing empties the value, restores focus to the input, and fires onSearch immediately", () => {
        const onSearch = vi.fn();
        render(<ControlledSearchField aria-label="Search" initialValue="eng" onSearch={onSearch} clearLabel="Clear search" />);

        fireEvent.click(screen.getByRole("button", { name: "Clear search" }));

        const input = screen.getByRole("searchbox", { name: "Search" });
        expect(input).toHaveValue("");
        expect(input).toHaveFocus();
        expect(onSearch).toHaveBeenCalledWith("");
        expect(screen.queryByRole("button", { name: "Clear search" })).not.toBeInTheDocument();
    });

    it("forwards aria-invalid to the real input so the danger-border treatment applies", () => {
        render(<ControlledSearchField aria-label="Search" aria-invalid="true" onSearch={vi.fn()} clearLabel="Clear search" />);
        expect(screen.getByRole("searchbox", { name: "Search" })).toHaveAttribute("aria-invalid", "true");
    });

    it("forwards disabled to the real input", () => {
        render(<ControlledSearchField aria-label="Search" disabled onSearch={vi.fn()} clearLabel="Clear search" />);
        expect(screen.getByRole("searchbox", { name: "Search" })).toBeDisabled();
    });

    it("applies a caller-supplied className to the outer wrapper, for layout only", () => {
        render(<ControlledSearchField aria-label="Search" className="mt-stack" onSearch={vi.fn()} clearLabel="Clear search" />);
        expect(screen.getByRole("searchbox", { name: "Search" }).parentElement).toHaveClass("mt-stack");
    });

    describe("as the child of Field", () => {
        it("associates Field's label with the real input, and keeps typing and the debounce working", () => {
            const onSearch = vi.fn();
            render(
                <Field label="Search jobs">
                    <ControlledSearchField onSearch={onSearch} clearLabel="Clear search" />
                </Field>,
            );

            const input = screen.getByLabelText("Search jobs");
            fireEvent.change(input, { target: { value: "staff engineer" } });
            expect(input).toHaveValue("staff engineer");

            vi.advanceTimersByTime(300);
            expect(onSearch).toHaveBeenCalledWith("staff engineer");
        });

        it("carries Field's error into aria-invalid on the real input", () => {
            render(
                <Field label="Search jobs" error="Query too short">
                    <ControlledSearchField onSearch={vi.fn()} clearLabel="Clear search" />
                </Field>,
            );

            expect(screen.getByLabelText("Search jobs")).toHaveAttribute("aria-invalid", "true");
        });
    });
});
