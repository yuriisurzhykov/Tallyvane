import { useEffect, useRef, type KeyboardEvent } from "react";
import type { RowData } from "@tanstack/react-table";
import { useDataTableContext } from "./data-table-context";
import { handleRovingKeyDown } from "./handle-roving-key-down";
import type { DataTableCellProps } from "./DataTable.types";

const CELL_CLASS = "min-w-0 flex items-center px-inline-tight text-body outline-none focus-visible:focus-ring";

/**
 * Roving-tabindex target: exactly one cell in the whole grid carries
 * `tabIndex={0}` at a time (`state.activeCell`), every other cell carries
 * `-1` — the standard WAI-ARIA "grid" keyboard pattern, hand-rolled because
 * no Base UI primitive covers a 2-D cell grid (`ADR-031`'s own named gap).
 * `onFocus` re-syncs `activeCell` when a cell is reached by a real Tab or a
 * mouse click rather than an arrow key, so the roving target never
 * disagrees with whatever the browser actually focused.
 */
export function Cell<TData extends RowData>({ cell, rowIndex, columnIndex }: DataTableCellProps<TData>) {
    const { state, actions, meta } = useDataTableContext<TData>();
    const isActive = state.activeCell.rowIndex === rowIndex && state.activeCell.columnIndex === columnIndex;
    const ref = useRef<HTMLDivElement>(null);

    /** See `ExpandToggleCell`'s identical effect — real DOM focus never follows a `tabIndex` change on its own. */
    useEffect(() => {
        if (isActive) ref.current?.focus();
    }, [isActive]);

    function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
        handleRovingKeyDown(event, { rowIndex, columnIndex, columnCount: meta.columnCount, moveActiveCell: actions.moveActiveCell });
    }

    return (
        <div
            ref={ref}
            role="gridcell"
            aria-colindex={columnIndex + 1}
            tabIndex={isActive ? 0 : -1}
            onKeyDown={handleKeyDown}
            onFocus={() => { actions.moveActiveCell(rowIndex, columnIndex); }}
            style={{ flex: `${ String(cell.column.getSize()) } 1 0%` }}
            className={CELL_CLASS}
        >
            <meta.table.FlexRender cell={cell} />
        </div>
    );
}
