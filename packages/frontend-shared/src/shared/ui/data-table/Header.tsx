import { ArrowDown, ArrowUp, ChevronsUpDown } from "lucide-react";
import { cn, CONTROL_ICON_CLASS } from "../../lib";
import { Truncate } from "../truncate";
import { useDataTableContext } from "./data-table-context";
import type { DataTableHeaderProps } from "./DataTable.types";

const HEADER_ROWGROUP_CLASS = "flex-none overflow-x-hidden border-b border-border-subtle";
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
    if (sorted === "asc") return <ArrowUp className={CONTROL_ICON_CLASS} aria-hidden="true"/>;
    if (sorted === "desc") return <ArrowDown className={CONTROL_ICON_CLASS} aria-hidden="true"/>;
    return <ChevronsUpDown className={`${CONTROL_ICON_CLASS} opacity-60`} aria-hidden="true"/>;
}

/**
 * The sticky-headed requirement is met by *not* sharing Body's scroll
 * region, not by a literal `position: sticky` — see `Body`'s own doc
 * comment for the full reasoning. Sortable headers render a real `<button>`
 * (Tab-reachable, `Enter`/`Space` activate it natively) rather than
 * participating in `Body`'s roving-cell system: they are chrome around the
 * grid, not one of the cells the task's own "arrow keys move between
 * cells" describes.
 *
 * The synthetic expand column's leading cell is a `gridcell`, not a
 * `columnheader`. It is alignment chrome, not a heading — a blank
 * `columnheader` fails axe `empty-table-header`, and that rule's own
 * incorrect example is exactly `aria-label` on an empty header.
 *
 * `overflow-x-hidden` plus `headerScrollElementRef` (`Body`'s own comment
 * has the horizontal-scroll story): this rowgroup never shows its own
 * scrollbar, but `Body`'s `onScroll` sets its `scrollLeft` directly, so the
 * header's columns stay aligned with the body's without the two elements
 * ever sharing one scroll container.
 */
export function Header({ className }: DataTableHeaderProps) {
    const { meta } = useDataTableContext();
    const headerGroups = meta.table.getHeaderGroups();

    return (
        <div ref={ meta.headerScrollElementRef } role="rowgroup" className={ cn(HEADER_ROWGROUP_CLASS, className) }>
            { headerGroups.map((headerGroup, groupIndex) => (
                <div role="row" key={ headerGroup.id } aria-rowindex={ groupIndex + 1 } className={ HEADER_ROW_CLASS }>
                    { meta.hasRowExpansion ? (
                        <div
                            role="gridcell"
                            aria-colindex={ 1 }
                            style={ { flex: "0 0 var(--control-height-sm)" } }
                            className={ HEADER_CELL_CLASS }
                        />
                    ) : null }
                    { headerGroup.headers.map((header, headerIndex) => {
                        const canSort = header.column.getCanSort();
                        const sorted = header.column.getIsSorted();
                        return (
                            <div
                                role="columnheader"
                                key={ header.id }
                                aria-colindex={ headerIndex + 1 + (meta.hasRowExpansion ? 1 : 0) }
                                aria-sort={ canSort ? sortAriaValue(sorted) : undefined }
                                aria-colspan={ header.colSpan > 1 ? header.colSpan : undefined }
                                style={ { flex: `${ String(header.getSize()) } 1 0%`, minWidth: header.getSize() } }
                                className={ HEADER_CELL_CLASS }
                            >
                                { header.isPlaceholder ? null : canSort ? (
                                    <button type="button" className={ SORT_BUTTON_CLASS }
                                            onClick={ header.column.getToggleSortingHandler() }>
                                        <Truncate className="min-w-0">
                                            <meta.table.FlexRender header={ header }/>
                                        </Truncate>
                                        <SortIcon sorted={ sorted }/>
                                    </button>
                                ) : (
                                    <Truncate className="min-w-0">
                                        <meta.table.FlexRender header={ header }/>
                                    </Truncate>
                                ) }
                            </div>
                        );
                    }) }
                </div>
            )) }
        </div>
    );
}
