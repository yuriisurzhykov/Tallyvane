"use client";

import { useMemo } from "react";
import { useTable, type RowData } from "@tanstack/react-table";
import { cn } from "../../lib";
import { dataTableFeatures } from "./table-features";
import { DataTableContext } from "./data-table-context";
import { useDataTableInteraction } from "./use-data-table-interaction";
import type { DataTableContextValue, DataTableRootProps } from "./DataTable.types";

const ROOT_CLASS = "flex h-full flex-col";

/**
 * Renders a real `<div role="grid">` — unlike `Select.Root` (no DOM at all),
 * this compound's `Header` and `Body` are separate, non-nested siblings (see
 * `Body`'s own doc comment for why they are not one shared scroll region),
 * so something has to be the element that actually owns the `grid` role and
 * groups them. That div carries no visual treatment of its own, the same
 * "grouping div, no styling" reasoning `Collapsible.Root`'s own doc comment
 * already gives for an identical shape.
 */
export function Root<TData extends RowData>({
    data,
    columns,
    getRowId,
    renderExpandedRow,
    expandRowLabel,
    collapseRowLabel,
    className,
    children,
    ...ariaProps
}: DataTableRootProps<TData>) {
    const table = useTable({
        features: dataTableFeatures,
        data,
        columns,
        ...(getRowId ? { getRowId } : {}),
    });
    const rows = table.getRowModel().rows;
    const hasRowExpansion = renderExpandedRow !== undefined;
    const columnCount = table.getAllLeafColumns().length + (hasRowExpansion ? 1 : 0);

    const { expandedRowIds, toggleRowExpanded, activeCell, moveActiveCell, scrollElementRef, virtualizer } =
        useDataTableInteraction(rows, columnCount);

    /**
     * `useVirtualizer`'s own imperative instance is exactly `SKILL.md` §5's
     * named exception to "don't memoize by default" — and, per that same
     * section's context, neither app has `reactCompiler: true` set yet, so
     * nothing downstream of this render is memoizing it automatically
     * either. Every context consumer (`Header`, `Body`, every mounted `Row`
     * and `Cell`) reads through this one object, so a fresh reference here
     * on every `Root` render would re-render the entire visible window on
     * any unrelated parent re-render.
     */
    const contextValue = useMemo<DataTableContextValue<TData>>(
        () => ({
            state: { expandedRowIds, activeCell },
            actions: { toggleRowExpanded, moveActiveCell },
            meta: {
                table,
                virtualizer,
                scrollElementRef,
                ...(renderExpandedRow ? { renderExpandedRow } : {}),
                ...(expandRowLabel ? { expandRowLabel } : {}),
                ...(collapseRowLabel ? { collapseRowLabel } : {}),
                hasRowExpansion,
                columnCount,
                rowCount: rows.length,
            },
        }),
        [
            expandedRowIds,
            activeCell,
            toggleRowExpanded,
            moveActiveCell,
            table,
            virtualizer,
            renderExpandedRow,
            expandRowLabel,
            collapseRowLabel,
            hasRowExpansion,
            columnCount,
            rows.length,
        ],
    );

    return (
        <DataTableContext value={contextValue as DataTableContextValue<RowData>}>
            <div role="grid" aria-rowcount={rows.length} aria-colcount={columnCount} className={cn(ROOT_CLASS, className)} {...ariaProps}>
                {children}
            </div>
        </DataTableContext>
    );
}
