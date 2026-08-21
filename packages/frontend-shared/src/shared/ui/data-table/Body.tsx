import { cn } from "../../lib";
import { useDataTableContext } from "./data-table-context";
import { Row } from "./Row";
import type { DataTableBodyProps } from "./DataTable.types";

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
export function Body({ className }: DataTableBodyProps) {
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
