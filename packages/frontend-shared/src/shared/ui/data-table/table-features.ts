import {
    columnSizingFeature,
    createSortedRowModel,
    rowSortingFeature,
    tableFeatures,
    type ColumnDef as TanStackColumnDef,
    type RowData,
} from "@tanstack/react-table";

/**
 * Registered once, module scope, per `create-table-hook`'s own "Creating the
 * factory during render" mistake — a fresh `tableFeatures()` object every
 * render would give `useTable` a new `features` reference and invalidate
 * everything memoized against it. `columnSizingFeature` is registered for
 * its static `size`/`getSize()` surface only (a relative flex weight — see
 * `Cell`'s and `Header`'s own `flex: <size> 1 0%` styles) — `columnResizingFeature` (drag-to-resize) is not
 * registered: no known call site needs interactive resize yet (YAGNI), and
 * adding it later is additive, not a breaking change to this module.
 */
export const dataTableFeatures = tableFeatures({
    rowSortingFeature,
    sortedRowModel: createSortedRowModel(),
    columnSizingFeature,
});

export type DataTableFeatures = typeof dataTableFeatures;

/**
 * TanStack's own `ColumnDef`, re-exported rather than redefined — but
 * specialized to this component's fixed, internal `dataTableFeatures`
 * registration so a caller never needs its own `tableFeatures()` call just
 * to name a column. That specialization is the one thing standing between
 * "re-export" and "redefine": `ColumnDef<TFeatures, TData, TValue>` needs a
 * `TFeatures` argument, and the only alternative — exporting the raw,
 * three-parameter generic — would force every caller to import
 * `tableFeatures`/`rowSortingFeature`/`columnSizingFeature` themselves,
 * which is exactly the raw-hook leakage the compound contract exists to
 * prevent (`patterns.md` §2: "inner parts... only read this component's own
 * context").
 */
export type DataTableColumnDef<TData extends RowData, TValue = unknown> = TanStackColumnDef<DataTableFeatures, TData, TValue>;
