import type { KeyboardEvent } from "react";
import type { DataTableActions } from "./DataTable.types";

export interface RovingKeyDownContext {
    readonly rowIndex: number;
    readonly columnIndex: number;
    readonly columnCount: number;
    readonly moveActiveCell: DataTableActions["moveActiveCell"];
}

/**
 * Shared by `Cell` and the expand-toggle button: arrow keys move the active
 * position by one row/column, `Home`/`End` jump to the current row's first
 * and last column — per this component's own required shape, row-scoped
 * jumps, not a whole-grid `Ctrl+Home`/`Ctrl+End` (not asked for; noted as a
 * real, deliberate scope cut in the README rather than built on a guess).
 *
 * Takes one context object rather than four positional coordinates plus a
 * callback: `rowIndex`/`columnIndex`/`columnCount`/`moveActiveCell` are one
 * cohesive "where am I, and how do I move" contract, not four independent
 * arguments a caller could plausibly reorder by mistake.
 */
export function handleRovingKeyDown(event: KeyboardEvent<HTMLElement>, context: RovingKeyDownContext): void {
    const { rowIndex, columnIndex, columnCount, moveActiveCell } = context;
    switch (event.key) {
        case "ArrowUp":
            event.preventDefault();
            moveActiveCell(rowIndex - 1, columnIndex);
            break;
        case "ArrowDown":
            event.preventDefault();
            moveActiveCell(rowIndex + 1, columnIndex);
            break;
        case "ArrowLeft":
            event.preventDefault();
            moveActiveCell(rowIndex, columnIndex - 1);
            break;
        case "ArrowRight":
            event.preventDefault();
            moveActiveCell(rowIndex, columnIndex + 1);
            break;
        case "Home":
            event.preventDefault();
            moveActiveCell(rowIndex, 0);
            break;
        case "End":
            event.preventDefault();
            moveActiveCell(rowIndex, columnCount - 1);
            break;
        default:
            break;
    }
}
