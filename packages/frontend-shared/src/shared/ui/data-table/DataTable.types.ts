import type { Cell as TableCell, ReactTable, Row as TableRow, RowData } from "@tanstack/react-table";
import type { Virtualizer } from "@tanstack/react-virtual";
import type { ReactNode, RefObject } from "react";
import type { DataTableColumnDef, DataTableFeatures } from "./table-features";

export interface DataTableActiveCell {
    readonly rowIndex: number;
    readonly columnIndex: number;
}

export interface DataTableState {
    readonly expandedRowIds: Readonly<Record<string, boolean>>;
    readonly activeCell: DataTableActiveCell;
}

export interface DataTableActions {
    readonly toggleRowExpanded: (rowId: string) => void;
    /** Absolute target coordinates, clamped against the live row/column counts; scrolls the row into view when it changes. */
    readonly moveActiveCell: (rowIndex: number, columnIndex: number) => void;
}

export interface DataTableMeta<TData extends RowData> {
    readonly table: ReactTable<DataTableFeatures, TData>;
    readonly virtualizer: Virtualizer<HTMLDivElement, HTMLDivElement>;
    readonly scrollElementRef: RefObject<HTMLDivElement | null>;
    readonly renderExpandedRow?: (row: TData) => ReactNode;
    readonly expandRowLabel?: string;
    readonly collapseRowLabel?: string;
    readonly hasRowExpansion: boolean;
    /** Leaf columns plus the synthetic expand-toggle column, if any — the grid's real `aria-colcount` and the upper bound for `moveActiveCell`. */
    readonly columnCount: number;
    readonly rowCount: number;
}

export interface DataTableContextValue<TData extends RowData> {
    readonly state: DataTableState;
    readonly actions: DataTableActions;
    readonly meta: DataTableMeta<TData>;
}

export type DataTableExpansionProps<TData extends RowData> =
    | {
          /** Rendered inside a `Collapsible.Panel` under the toggled-open row — per `COMPONENTS.md` §5, row expansion is `Collapsible` inside a `DataTable` row, not a second mechanism. */
          readonly renderExpandedRow: (row: TData) => ReactNode;
          /** Accessible name for the collapsed toggle. No literal text in this file (`COMPONENTS.md` §12) — the caller's own copy, same convention `IconButton.label` already establishes for every icon-only control. */
          readonly expandRowLabel: string;
          /** Accessible name for the expanded toggle. */
          readonly collapseRowLabel: string;
      }
    | {
          readonly renderExpandedRow?: never;
          readonly expandRowLabel?: never;
          readonly collapseRowLabel?: never;
      };

export type DataTableRootProps<TData extends RowData> = {
    readonly data: readonly TData[];
    readonly columns: readonly DataTableColumnDef<TData>[];
    readonly getRowId?: (row: TData, index: number) => string;
    /** The grid's accessible name — required, since this component holds no domain copy of its own to synthesize one from (`COMPONENTS.md` §12). */
    readonly "aria-label": string;
    readonly children: ReactNode;
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
} & DataTableExpansionProps<TData>;

export interface DataTableHeaderProps {
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

export interface DataTableBodyProps {
    /** Layout and position only — see `COMPONENTS.md` §11. The scroll container needs a real, determinate height from its own ancestor chain; see `scroll-area/README.md`'s dated entry for the same `h-full`-needs-a-real-ancestor lesson, which applies identically here. */
    readonly className?: string;
}

export interface DataTableRowProps<TData extends RowData> {
    readonly row: TableRow<DataTableFeatures, TData>;
    readonly rowIndex: number;
    readonly ariaRowIndex: number;
    readonly start: number;
}

export interface DataTableCellProps<TData extends RowData> {
    readonly cell: TableCell<DataTableFeatures, TData>;
    readonly rowIndex: number;
    readonly columnIndex: number;
}
