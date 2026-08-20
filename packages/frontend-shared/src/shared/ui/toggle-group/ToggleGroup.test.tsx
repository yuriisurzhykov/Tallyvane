import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { ToggleGroup } from "./ToggleGroup";
import { Toggle } from "../toggle";

describe("ToggleGroup", () => {
    it("is single-select by default: pressing a second toggle unpresses the first, uncontrolled", () => {
        render(
            <ToggleGroup defaultValue={["table"]}>
                <Toggle value="table">Table</Toggle>
                <Toggle value="board">Board</Toggle>
            </ToggleGroup>,
        );

        const table = screen.getByRole("button", { name: "Table" });
        const board = screen.getByRole("button", { name: "Board" });
        expect(table).toHaveAttribute("aria-pressed", "true");
        expect(board).toHaveAttribute("aria-pressed", "false");

        fireEvent.click(board);

        expect(board).toHaveAttribute("aria-pressed", "true");
        expect(table).toHaveAttribute("aria-pressed", "false");
    });

    it("allows more than one pressed toggle when multiple is set", () => {
        render(
            <ToggleGroup multiple defaultValue={["table"]}>
                <Toggle value="table">Table</Toggle>
                <Toggle value="board">Board</Toggle>
            </ToggleGroup>,
        );

        const table = screen.getByRole("button", { name: "Table" });
        const board = screen.getByRole("button", { name: "Board" });

        fireEvent.click(board);

        expect(table).toHaveAttribute("aria-pressed", "true");
        expect(board).toHaveAttribute("aria-pressed", "true");
    });

    it("reports the next value array to a controlled caller on click", () => {
        const onValueChange = vi.fn();
        render(
            <ToggleGroup value={["table"]} onValueChange={onValueChange}>
                <Toggle value="table">Table</Toggle>
                <Toggle value="board">Board</Toggle>
            </ToggleGroup>,
        );

        fireEvent.click(screen.getByRole("button", { name: "Board" }));

        expect(onValueChange).toHaveBeenCalledTimes(1);
        expect(onValueChange.mock.calls[0]?.[0]).toEqual(["board"]);
    });

    it("switches to a column layout when the orientation is vertical", () => {
        render(
            <ToggleGroup orientation="vertical" defaultValue={[]}>
                <Toggle value="table">Table</Toggle>
                <Toggle value="board">Board</Toggle>
            </ToggleGroup>,
        );

        expect(screen.getByRole("group")).toHaveClass("data-[orientation=vertical]:flex-col");
        expect(screen.getByRole("group")).toHaveAttribute("data-orientation", "vertical");
    });

    it("merges a caller-provided className with its own classes", () => {
        render(
            <ToggleGroup className="mt-stack" defaultValue={[]}>
                <Toggle value="table">Table</Toggle>
            </ToggleGroup>,
        );

        expect(screen.getByRole("group")).toHaveClass("inline-flex");
        expect(screen.getByRole("group")).toHaveClass("mt-stack");
    });
});
