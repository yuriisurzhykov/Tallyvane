import { useCallback, useRef, useState, type RefObject } from "react";
import type { Row as TableRow, RowData } from "@tanstack/react-table";
import { useVirtualizer, type Virtualizer } from "@tanstack/react-virtual";
import { ROW_HEIGHT_PX, OVERSCAN } from "./constants";
import { clamp } from "./data-table-context";
import type { DataTableActiveCell } from "./DataTable.types";
import type { DataTableFeatures } from "./table-features";

export interface DataTableInteraction {
    readonly expandedRowIds: Readonly<Record<string, boolean>>;
    readonly toggleRowExpanded: (rowId: string) => void;
    readonly activeCell: DataTableActiveCell;
    readonly moveActiveCell: (rowIndex: number, columnIndex: number) => void;
    readonly scrollElementRef: RefObject<HTMLDivElement | null>;
    readonly virtualizer: Virtualizer<HTMLDivElement, HTMLDivElement>;
}

/**
 * Everything `Root` owns beyond the TanStack `table` instance itself: row
 * expansion, the roving active cell, and the virtualizer they both need —
 * `moveActiveCell` genuinely calls the virtualizer's own `scrollToIndex`,
 * which is why this is one hook rather than two independent ones.
 *
 * Takes the live TanStack `rows` array, not a derived id list: `getItemKey`
 * depends on it, and TanStack's own row model only produces a new `rows`
 * reference when data or columns actually change — a derived array built
 * fresh every render (e.g. `rows.map(r => r.id)`) would defeat that and
 * reconstruct the virtualizer on every unrelated render, the exact mistake
 * this component's own comments elsewhere warn against.
 */
export function useDataTableInteraction<TData extends RowData>(
    rows: readonly TableRow<DataTableFeatures, TData>[],
    columnCount: number,
): DataTableInteraction {
    const [expandedRowIds, setExpandedRowIds] = useState<Record<string, boolean>>({});
    const [activeCell, setActiveCell] = useState<DataTableActiveCell>({ rowIndex: 0, columnIndex: 0 });
    const scrollElementRef = useRef<HTMLDivElement>(null);

    const getScrollElement = useCallback(() => scrollElementRef.current, []);
    const getItemKey = useCallback((index: number) => rows[index]?.id ?? index, [rows]);
    // @architecture-exception rule=no-raw-dimension-value adr=ADR-042
    //   reason=useVirtualizer.estimateSize cannot read a CSS variable
    const estimateSize = useCallback(() => ROW_HEIGHT_PX, []);

    const virtualizer = useVirtualizer<HTMLDivElement, HTMLDivElement>({
        count: rows.length,
        getScrollElement,
        estimateSize,
        getItemKey,
        overscan: OVERSCAN,
    });

    const toggleRowExpanded = useCallback((rowId: string) => {
        setExpandedRowIds((previous) => ({ ...previous, [rowId]: !previous[rowId] }));
    }, []);

    const moveActiveCell = useCallback(
        (rowIndex: number, columnIndex: number) => {
            setActiveCell((previous) => {
                const nextRowIndex = clamp(rowIndex, 0, Math.max(rows.length - 1, 0));
                const nextColumnIndex = clamp(columnIndex, 0, Math.max(columnCount - 1, 0));
                if (nextRowIndex === previous.rowIndex && nextColumnIndex === previous.columnIndex) return previous;
                if (nextRowIndex !== previous.rowIndex) virtualizer.scrollToIndex(nextRowIndex, { align: "auto" });
                return { rowIndex: nextRowIndex, columnIndex: nextColumnIndex };
            });
        },
        [rows.length, columnCount, virtualizer],
    );

    return { expandedRowIds, toggleRowExpanded, activeCell, moveActiveCell, scrollElementRef, virtualizer };
}
