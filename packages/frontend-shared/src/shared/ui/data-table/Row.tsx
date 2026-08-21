import { useEffect, useRef, type KeyboardEvent } from "react";
import type { RowData } from "@tanstack/react-table";
import { ChevronDown, ChevronRight } from "lucide-react";
import { Collapsible } from "../collapsible";
import { CONTROL_ICON_CLASS } from "../../lib";
import { useDataTableContext } from "./data-table-context";
import { handleRovingKeyDown } from "./handle-roving-key-down";
import { Cell } from "./Cell";
import type { DataTableRowProps } from "./DataTable.types";

const ROW_CELLS_CLASS = "flex items-stretch border-b border-border-subtle transition-hover hover:bg-surface-row-hover";
const EXPAND_TOGGLE_CELL_CLASS = "flex items-center justify-center";

/**
 * One virtualized row. `ref={virtualizer.measureElement}` on the outer,
 * absolutely-positioned wrapper is what lets a toggled-open row's real
 * height (cells plus the expanded `Collapsible.Panel` below them) correct
 * the virtualizer's own `ROW_HEIGHT_PX` estimate and reflow every row below
 * it — including, honestly, during `Collapsible`'s own open/close height
 * transition, whose intermediate frames the same `ResizeObserver` also
 * measures. That is a real, visible interaction between the two libraries
 * (rows below an expanding one visibly shift as the transition plays), not
 * a bug; documented in this component's own README rather than fixed by
 * fighting `Collapsible`'s own established transition.
 */
export function Row<TData extends RowData>({ row, rowIndex, ariaRowIndex, start }: DataTableRowProps<TData>) {
    const { state, actions, meta } = useDataTableContext<TData>();
    const expanded = meta.hasRowExpansion && Boolean(state.expandedRowIds[row.id]);
    const cells = row.getAllCells();
    const columnOffset = meta.hasRowExpansion ? 1 : 0;

    const cellsRow = (
        <div role="row" aria-rowindex={ariaRowIndex} className={ROW_CELLS_CLASS} style={{ height: "var(--control-height-sm)" }}>
            {meta.hasRowExpansion ? (
                <div role="gridcell" aria-colindex={1} className={EXPAND_TOGGLE_CELL_CLASS} style={{ flex: "0 0 var(--control-height-sm)" }}>
                    <ExpandToggleCell rowIndex={rowIndex} expanded={expanded} />
                </div>
            ) : null}
            {cells.map((cell, cellIndex) => (
                <Cell key={cell.id} cell={cell} rowIndex={rowIndex} columnIndex={cellIndex + columnOffset} />
            ))}
        </div>
    );

    const wrapperStyle = { position: "absolute" as const, top: 0, left: 0, transform: `translateY(${String(start)}px)` };

    if (!meta.hasRowExpansion) {
        return (
            <div data-index={rowIndex} ref={meta.virtualizer.measureElement} className="w-full" style={wrapperStyle}>
                {cellsRow}
            </div>
        );
    }

    return (
        <div data-index={rowIndex} ref={meta.virtualizer.measureElement} className="w-full" style={wrapperStyle}>
            <Collapsible.Root open={expanded} onOpenChange={() => { actions.toggleRowExpanded(row.id); }}>
                {cellsRow}
                <Collapsible.Panel>{meta.renderExpandedRow?.(row.original)}</Collapsible.Panel>
            </Collapsible.Root>
        </div>
    );
}

function ExpandToggleCell({ rowIndex, expanded }: { readonly rowIndex: number; readonly expanded: boolean }) {
    const { state, actions, meta } = useDataTableContext();
    const isActive = state.activeCell.rowIndex === rowIndex && state.activeCell.columnIndex === 0;
    const ref = useRef<HTMLButtonElement>(null);

    /** Real DOM focus does not follow `tabIndex` on its own — the browser leaves focus wherever it was until something calls `.focus()`. A no-op on the already-focused element (mouse click, which focuses natively before `onFocus` even fires) is harmless; this is the one branch that matters for a keyboard-driven move. */
    useEffect(() => {
        if (isActive) ref.current?.focus();
    }, [isActive]);

    function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
        handleRovingKeyDown(event, { rowIndex, columnIndex: 0, columnCount: meta.columnCount, moveActiveCell: actions.moveActiveCell });
    }

    return (
        <Collapsible.Trigger
            ref={ref}
            aria-label={expanded ? meta.collapseRowLabel : meta.expandRowLabel}
            tabIndex={isActive ? 0 : -1}
            onKeyDown={handleKeyDown}
            onFocus={() => { actions.moveActiveCell(rowIndex, 0); }}
        >
            {expanded ? <ChevronDown className={CONTROL_ICON_CLASS} aria-hidden="true" /> : <ChevronRight className={CONTROL_ICON_CLASS} aria-hidden="true" />}
        </Collapsible.Trigger>
    );
}
