import { createContext, use } from "react";
import type { RowData } from "@tanstack/react-table";
import type { DataTableContextValue } from "./DataTable.types";

/**
 * `RowData` (`Record<string, any> | Array<any>`), not `any` — React context
 * itself is not generic, so one module-scoped context has to stand in for
 * every `TData` a caller instantiates `DataTable.Root` with. Widening to
 * `RowData` rather than `any` keeps this file's own strict/`no-explicit-any`
 * posture intact; `useDataTableContext` below is the one, documented,
 * narrow cast back to the caller's real `TData` — every other part reads
 * through that hook, never the context object directly.
 */
const DataTableContext = createContext<DataTableContextValue<RowData> | null>(null);

export { DataTableContext };

export function useDataTableContext<TData extends RowData = RowData>(): DataTableContextValue<TData> {
    const context = use(DataTableContext);
    if (!context) {
        throw new Error("DataTable.* must be used inside <DataTable.Root>");
    }
    return context as DataTableContextValue<TData>;
}

export function clamp(value: number, min: number, max: number): number {
    return Math.min(Math.max(value, min), max);
}
