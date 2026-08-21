"use client";

import { createContext, use, useCallback, useEffect, useMemo, useRef, useState, type KeyboardEvent, type ReactNode, type RefObject } from "react";
import {
    columnSizingFeature,
    createSortedRowModel,
    rowSortingFeature,
    tableFeatures,
    useTable,
    type Cell as TableCell,
    type ColumnDef as TanStackColumnDef,
    type ReactTable,
    type Row as TableRow,
    type RowData,
} from "@tanstack/react-table";
import { useVirtualizer, type Virtualizer } from "@tanstack/react-virtual";
import { ArrowDown, ArrowUp, ChevronDown, ChevronRight, ChevronsUpDown } from "lucide-react";
import { cn } from "../../lib";
import { Collapsible } from "../collapsible";

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
const dataTableFeatures = tableFeatures({
    rowSortingFeature,
    sortedRowModel: createSortedRowModel(),
    columnSizingFeature,
});

type DataTableFeatures = typeof dataTableFeatures;

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

/**
 * One fixed row height for this pass — `COMPONENTS.md` §5 states density
 * tokens are not built yet, so this ships a single value rather than a new
 * `data-density`-driven one. `32` (not a token reference) because
 * `useVirtualizer`'s own `estimateSize` needs a real JS pixel number for its
 * position math, which no CSS custom property can supply — the same
 * "genuinely tokenless numeric, needed for JS math" exception
 * `ScrollArea.tsx`'s `SCROLLBAR_THICKNESS` and `Grid.tsx`'s `columns` prop
 * already document. The value itself is not arbitrary: it equals
 * `--control-height-sm` (`dimension.8`, `2rem`) at the default root font
 * size, the same height role `Select`'s `sm` trigger already uses, chosen
 * because "dense" is this component's own explicit requirement. Real
 * pixel/CSS drift (a non-default root font size, browser zoom) is corrected
 * automatically by `ref={virtualizer.measureElement}` on every row below —
 * this constant only seeds the initial estimate before the first real
 * measurement lands.
 */
const ROW_HEIGHT_PX = 32;

/** Square, matching `ROW_HEIGHT_PX` — the expand-toggle cell is a small fixed column, not a proportional one. */
const EXPAND_COLUMN_WIDTH_PX = ROW_HEIGHT_PX;

/** Rows just outside the viewport that stay mounted, so an arrow key landing one row past the fold does not need to wait a full re-measure before it can be focused. */
const OVERSCAN = 8;

interface DataTableActiveCell {
    readonly rowIndex: number;
    readonly columnIndex: number;
}

interface DataTableState {
    readonly expandedRowIds: Readonly<Record<string, boolean>>;
    readonly activeCell: DataTableActiveCell;
}

interface DataTableActions {
    readonly toggleRowExpanded: (rowId: string) => void;
    /** Absolute target coordinates, clamped against the live row/column counts; scrolls the row into view when it changes. */
    readonly moveActiveCell: (rowIndex: number, columnIndex: number) => void;
}

interface DataTableMeta<TData extends RowData> {
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

interface DataTableContextValue<TData extends RowData> {
    readonly state: DataTableState;
    readonly actions: DataTableActions;
    readonly meta: DataTableMeta<TData>;
}

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

function useDataTableContext<TData extends RowData = RowData>(): DataTableContextValue<TData> {
    const context = use(DataTableContext);
    if (!context) {
        throw new Error("DataTable.* must be used inside <DataTable.Root>");
    }
    return context as DataTableContextValue<TData>;
}

function clamp(value: number, min: number, max: number): number {
    return Math.min(Math.max(value, min), max);
}

/* ------------------------------------------------------------------------ */
/* Root                                                                      */
/* ------------------------------------------------------------------------ */

type DataTableExpansionProps<TData extends RowData> =
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
function Root<TData extends RowData>({
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

    const [expandedRowIds, setExpandedRowIds] = useState<Record<string, boolean>>({});
    const [activeCell, setActiveCell] = useState<DataTableActiveCell>({ rowIndex: 0, columnIndex: 0 });
    const scrollElementRef = useRef<HTMLDivElement>(null);

    const getScrollElement = useCallback(() => scrollElementRef.current, []);
    const getItemKey = useCallback((index: number) => rows[index]?.id ?? index, [rows]);
    const estimateSize = useCallback(() => ROW_HEIGHT_PX, []);

    const virtualizer = useVirtualizer<HTMLDivElement, HTMLDivElement>({
        count: rows.length,
        getScrollElement,
        estimateSize,
        getItemKey,
        overscan: OVERSCAN,
    });

    const columnCount = table.getAllLeafColumns().length + (hasRowExpansion ? 1 : 0);

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

/* ------------------------------------------------------------------------ */
/* Header                                                                    */
/* ------------------------------------------------------------------------ */

export interface DataTableHeaderProps {
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

const HEADER_ROWGROUP_CLASS = "flex-none border-b border-border-subtle";
const HEADER_ROW_CLASS = "flex";
const HEADER_CELL_CLASS = "min-w-0 px-inline-tight text-caption text-text-muted";
const SORT_BUTTON_CLASS =
    "inline-flex w-full items-center gap-inline-tight rounded-control py-inline-tight text-left outline-none transition-hover hover:text-text-primary focus-visible:focus-ring";

function sortAriaValue(sorted: false | "asc" | "desc"): "ascending" | "descending" | "none" {
    if (sorted === "asc") return "ascending";
    if (sorted === "desc") return "descending";
    return "none";
}

function SortIcon({ sorted }: { readonly sorted: false | "asc" | "desc" }) {
    if (sorted === "asc") return <ArrowUp size={14} aria-hidden="true" />;
    if (sorted === "desc") return <ArrowDown size={14} aria-hidden="true" />;
    return <ChevronsUpDown size={14} aria-hidden="true" className="opacity-60" />;
}

/**
 * The sticky-headed requirement is met by *not* sharing Body's scroll
 * region, not by a literal `position: sticky` — see `Body`'s own doc
 * comment for the full reasoning. Sortable headers render a real `<button>`
 * (Tab-reachable, `Enter`/`Space` activate it natively) rather than
 * participating in `Body`'s roving-cell system: they are chrome around the
 * grid, not one of the cells the task's own "arrow keys move between
 * cells" describes.
 */
function Header({ className }: DataTableHeaderProps) {
    const { meta } = useDataTableContext();
    const headerGroups = meta.table.getHeaderGroups();

    return (
        <div role="rowgroup" className={cn(HEADER_ROWGROUP_CLASS, className)}>
            {headerGroups.map((headerGroup, groupIndex) => (
                <div role="row" key={headerGroup.id} aria-rowindex={groupIndex + 1} className={HEADER_ROW_CLASS}>
                    {meta.hasRowExpansion ? (
                        <div
                            role="columnheader"
                            aria-colindex={1}
                            style={{ flex: `0 0 ${EXPAND_COLUMN_WIDTH_PX}px` }}
                            className={HEADER_CELL_CLASS}
                        />
                    ) : null}
                    {headerGroup.headers.map((header, headerIndex) => {
                        const canSort = header.column.getCanSort();
                        const sorted = header.column.getIsSorted();
                        return (
                            <div
                                role="columnheader"
                                key={header.id}
                                aria-colindex={headerIndex + 1 + (meta.hasRowExpansion ? 1 : 0)}
                                aria-sort={canSort ? sortAriaValue(sorted) : undefined}
                                aria-colspan={header.colSpan > 1 ? header.colSpan : undefined}
                                style={{ flex: `${header.getSize()} 1 0%` }}
                                className={HEADER_CELL_CLASS}
                            >
                                {header.isPlaceholder ? null : canSort ? (
                                    <button type="button" className={SORT_BUTTON_CLASS} onClick={header.column.getToggleSortingHandler()}>
                                        <meta.table.FlexRender header={header} />
                                        <SortIcon sorted={sorted} />
                                    </button>
                                ) : (
                                    <meta.table.FlexRender header={header} />
                                )}
                            </div>
                        );
                    })}
                </div>
            ))}
        </div>
    );
}

/* ------------------------------------------------------------------------ */
/* Body                                                                     */
/* ------------------------------------------------------------------------ */

export interface DataTableBodyProps {
    /** Layout and position only — see `COMPONENTS.md` §11. The scroll container needs a real, determinate height from its own ancestor chain; see `scroll-area/README.md`'s dated entry for the same `h-full`-needs-a-real-ancestor lesson, which applies identically here. */
    readonly className?: string;
}

const BODY_CLASS = "relative min-h-0 flex-1 overflow-y-auto overflow-x-hidden";

/**
 * The actual scroll/virtualization container — only the rows
 * `useVirtualizer` currently measures as visible/near-visible ever mount,
 * which is the entire reason `@tanstack/react-virtual` exists here over a
 * plain `<table>`. Takes no children: the caller's own column defs already
 * carry everything needed to render every cell, so there is nothing for a
 * caller to hand back through a render prop (`SKILL.md` §3.5 — a render
 * prop earns its place only when data flows back to the caller, and none
 * does here).
 *
 * **Why this is not one shared `position: sticky` region with `Header`.**
 * The more common "single scrollbar" recipe puts header and body rows in
 * one scroll container and pins the header via `position: sticky; top: 0`,
 * which also keeps a header in horizontal sync with body columns for free.
 * That recipe needs `Header` and `Body` to be the same element's children;
 * the task's own part list makes `Body` "the actual scroll/virtualization
 * container" on its own, i.e. a second, separate element from `Header`.
 * With two separate elements, `position: sticky` buys nothing (`Header`
 * never scrolls to begin with) and reintroduces exactly the z-index
 * question the single-container recipe exists to avoid (a sibling's
 * `position: sticky` needs an explicit stacking order against `Body`'s own
 * absolutely-positioned virtual rows, and no existing named z-index role —
 * `sidebar`, `popover`, `modal`, `toast`, `tooltip` — describes "a
 * non-overlay header pinned above a sibling scroll region"). Resolved here
 * by not needing sticky at all: `Header` is simply outside the scrolling
 * region, and columns size by flex proportion (`flex: <size> 1 0%`, not an
 * absolute pixel width) rather than a fixed pixel width, so there is no
 * horizontal overflow for the two elements to fall out of sync over in the
 * first place. Flagged in this component's own README as a real design
 * decision, not a silent simplification.
 */
function Body({ className }: DataTableBodyProps) {
    const { meta } = useDataTableContext();
    const virtualItems = meta.virtualizer.getVirtualItems();
    const headerRowCount = meta.table.getHeaderGroups().length;

    return (
        <div
            ref={meta.scrollElementRef}
            role="rowgroup"
            data-testid="data-table-body"
            // Opt-in marker `contrast-wcag.ts`'s virtualized-scroll-clip check looks for: this container clips overscan rows by design, so a node found only partially inside it is not really obscured.
            data-virtualized-scroll-container=""
            className={cn(BODY_CLASS, className)}
        >
            <div style={{ height: meta.virtualizer.getTotalSize(), position: "relative" }}>
                {virtualItems.map((virtualItem) => {
                    const row = meta.table.getRowModel().rows[virtualItem.index];
                    if (!row) return null;
                    return (
                        <Row
                            key={virtualItem.key}
                            row={row}
                            rowIndex={virtualItem.index}
                            ariaRowIndex={headerRowCount + virtualItem.index + 1}
                            start={virtualItem.start}
                        />
                    );
                })}
            </div>
        </div>
    );
}

/* ------------------------------------------------------------------------ */
/* Row                                                                       */
/* ------------------------------------------------------------------------ */

export interface DataTableRowProps<TData extends RowData> {
    readonly row: TableRow<DataTableFeatures, TData>;
    readonly rowIndex: number;
    readonly ariaRowIndex: number;
    readonly start: number;
}

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
function Row<TData extends RowData>({ row, rowIndex, ariaRowIndex, start }: DataTableRowProps<TData>) {
    const { state, actions, meta } = useDataTableContext<TData>();
    const expanded = meta.hasRowExpansion && Boolean(state.expandedRowIds[row.id]);
    const cells = row.getAllCells();
    const columnOffset = meta.hasRowExpansion ? 1 : 0;

    const cellsRow = (
        <div role="row" aria-rowindex={ariaRowIndex} className={ROW_CELLS_CLASS} style={{ height: ROW_HEIGHT_PX }}>
            {meta.hasRowExpansion ? (
                <div role="gridcell" aria-colindex={1} className={EXPAND_TOGGLE_CELL_CLASS} style={{ flex: `0 0 ${EXPAND_COLUMN_WIDTH_PX}px` }}>
                    <ExpandToggleCell rowIndex={rowIndex} expanded={expanded} />
                </div>
            ) : null}
            {cells.map((cell, cellIndex) => (
                <Cell key={cell.id} cell={cell} rowIndex={rowIndex} columnIndex={cellIndex + columnOffset} />
            ))}
        </div>
    );

    const wrapperStyle = { position: "absolute" as const, top: 0, left: 0, width: "100%", transform: `translateY(${start}px)` };

    if (!meta.hasRowExpansion) {
        return (
            <div data-index={rowIndex} ref={meta.virtualizer.measureElement} style={wrapperStyle}>
                {cellsRow}
            </div>
        );
    }

    return (
        <div data-index={rowIndex} ref={meta.virtualizer.measureElement} style={wrapperStyle}>
            <Collapsible.Root open={expanded} onOpenChange={() => actions.toggleRowExpanded(row.id)}>
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
        handleRovingKeyDown(event, rowIndex, 0, meta.columnCount, actions.moveActiveCell);
    }

    return (
        <Collapsible.Trigger
            ref={ref}
            aria-label={expanded ? meta.collapseRowLabel : meta.expandRowLabel}
            tabIndex={isActive ? 0 : -1}
            onKeyDown={handleKeyDown}
            onFocus={() => actions.moveActiveCell(rowIndex, 0)}
        >
            {expanded ? <ChevronDown size={16} aria-hidden="true" /> : <ChevronRight size={16} aria-hidden="true" />}
        </Collapsible.Trigger>
    );
}

/* ------------------------------------------------------------------------ */
/* Cell                                                                      */
/* ------------------------------------------------------------------------ */

export interface DataTableCellProps<TData extends RowData> {
    readonly cell: TableCell<DataTableFeatures, TData, unknown>;
    readonly rowIndex: number;
    readonly columnIndex: number;
}

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
function Cell<TData extends RowData>({ cell, rowIndex, columnIndex }: DataTableCellProps<TData>) {
    const { state, actions, meta } = useDataTableContext<TData>();
    const isActive = state.activeCell.rowIndex === rowIndex && state.activeCell.columnIndex === columnIndex;
    const ref = useRef<HTMLDivElement>(null);

    /** See `ExpandToggleCell`'s identical effect — real DOM focus never follows a `tabIndex` change on its own. */
    useEffect(() => {
        if (isActive) ref.current?.focus();
    }, [isActive]);

    function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
        handleRovingKeyDown(event, rowIndex, columnIndex, meta.columnCount, actions.moveActiveCell);
    }

    return (
        <div
            ref={ref}
            role="gridcell"
            aria-colindex={columnIndex + 1}
            tabIndex={isActive ? 0 : -1}
            onKeyDown={handleKeyDown}
            onFocus={() => actions.moveActiveCell(rowIndex, columnIndex)}
            style={{ flex: `${cell.column.getSize()} 1 0%` }}
            className={CELL_CLASS}
        >
            <meta.table.FlexRender cell={cell} />
        </div>
    );
}

/**
 * Shared by `Cell` and the expand-toggle button: arrow keys move the active
 * position by one row/column, `Home`/`End` jump to the current row's first
 * and last column — per this component's own required shape, row-scoped
 * jumps, not a whole-grid `Ctrl+Home`/`Ctrl+End` (not asked for; noted as a
 * real, deliberate scope cut in the README rather than built on a guess).
 */
function handleRovingKeyDown(
    event: KeyboardEvent<HTMLElement>,
    rowIndex: number,
    columnIndex: number,
    columnCount: number,
    moveActiveCell: DataTableActions["moveActiveCell"],
): void {
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

/* ------------------------------------------------------------------------ */

/**
 * Tier 1 — the single most load-bearing component in the product
 * (`COMPONENTS.md` §4: "the pipeline is a thousand rows at 16 ms a frame").
 * `Root`/`Header`/`Body`/`Row`/`Cell`, backed by `@tanstack/react-table` and
 * `@tanstack/react-virtual` end to end (`ADR-031`'s own named gap: a
 * virtualized table is headless, and Base UI does not cover it). Every part
 * below `Root` reads the `state`/`actions`/`meta` context contract
 * (`patterns.md` §2) — none of them calls `useTable`/`useVirtualizer`
 * directly.
 */
export const DataTable = { Root, Header, Body, Row, Cell };
