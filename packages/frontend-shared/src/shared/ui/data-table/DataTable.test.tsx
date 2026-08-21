import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { DataTable, type DataTableColumnDef } from "./DataTable";

interface Person {
    readonly id: string;
    readonly name: string;
    readonly role: string;
    readonly yearsOfExperience: number;
}

const PEOPLE: Person[] = [
    { id: "1", name: "Ada Lovelace", role: "Engineer", yearsOfExperience: 12 },
    { id: "2", name: "Grace Hopper", role: "Engineer", yearsOfExperience: 20 },
    { id: "3", name: "Alan Turing", role: "Researcher", yearsOfExperience: 8 },
    { id: "4", name: "Katherine Johnson", role: "Mathematician", yearsOfExperience: 15 },
    { id: "5", name: "Margaret Hamilton", role: "Engineer", yearsOfExperience: 18 },
];

const COLUMNS: DataTableColumnDef<Person>[] = [
    { accessorKey: "name", header: "Name" },
    { accessorKey: "role", header: "Role", enableSorting: false },
    { accessorKey: "yearsOfExperience", header: "Years" },
];
const COLUMN_COUNT = COLUMNS.length;

/**
 * jsdom never computes real layout (`offsetHeight`/`offsetWidth` are always
 * `0`), so `@tanstack/react-virtual`'s own default `observeElementRect`
 * (which reads exactly those two properties, per its own source) would read
 * the scroll container as zero-height and `calculateRange` would then bail
 * out (`outerSize === 0`) before ever computing a visible window — the
 * `getVirtualItems()` gap the task's own testing strategy names explicitly.
 * Stubbing `offsetHeight` per-element rather than as one blanket value is
 * required here, unlike `ScrollArea.test.tsx`'s single `scrollHeight`
 * stub, because this component has two genuinely different things that
 * need a real height: the scroll container (needs to be tall enough that
 * every row in a small fixture dataset falls inside the virtualizer's
 * visible range) and each row (needs to report this component's own fixed
 * row height, `ROW_HEIGHT_PX`, so the position math the virtualizer computes
 * from `estimateSize` stays consistent with what a real render would
 * measure). `data-testid="data-table-body"` is `Body`'s own stable hook for
 * "the real scroll element" — the same purpose `ScrollArea.tsx`'s own
 * `data-testid="scroll-area-viewport"` already serves.
 */
const ROW_HEIGHT_PX = 32;
const VIEWPORT_HEIGHT_PX = 600;

let originalOffsetHeight: PropertyDescriptor | undefined;

beforeEach(() => {
    originalOffsetHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "offsetHeight");
    Object.defineProperty(HTMLElement.prototype, "offsetHeight", {
        configurable: true,
        get(this: HTMLElement) {
            return this.getAttribute("data-testid") === "data-table-body" ? VIEWPORT_HEIGHT_PX : ROW_HEIGHT_PX;
        },
    });
});

afterEach(() => {
    if (originalOffsetHeight) Object.defineProperty(HTMLElement.prototype, "offsetHeight", originalOffsetHeight);
});

function renderBasicTable(className?: string) {
    return render(
        <DataTable.Root
            data={PEOPLE}
            columns={COLUMNS}
            getRowId={(row) => row.id}
            aria-label="People"
            {...(className ? { className } : {})}
        >
            <DataTable.Header />
            <DataTable.Body />
        </DataTable.Root>,
    );
}

function getGridCells(): Promise<HTMLElement[]> {
    return waitFor(() => {
        const cells = screen.getAllByRole("gridcell");
        expect(cells.length).toBe(PEOPLE.length * COLUMN_COUNT);
        return cells;
    });
}

function cellAt(cells: HTMLElement[], rowIndex: number, columnIndex: number): HTMLElement {
    const cell = cells[rowIndex * COLUMN_COUNT + columnIndex];
    if (!cell) throw new Error(`No cell at row ${rowIndex}, column ${columnIndex}`);
    return cell;
}

describe("DataTable", () => {
    describe("column rendering", () => {
        it("renders a labeled grid with a columnheader per column", () => {
            renderBasicTable();

            expect(screen.getByRole("grid", { name: "People" })).toBeInTheDocument();
            expect(screen.getByRole("columnheader", { name: "Name" })).toBeInTheDocument();
            expect(screen.getByRole("columnheader", { name: "Role" })).toBeInTheDocument();
            expect(screen.getByRole("columnheader", { name: "Years" })).toBeInTheDocument();
        });

        it("renders one row per data item, virtualized rows included, with every column's value", async () => {
            renderBasicTable();

            await waitFor(() => {
                for (const person of PEOPLE) {
                    // `name` is unique per person; `role` repeats across several people
                    // (three "Engineer"s), so it is asserted once via `getAllByText` below
                    // rather than per-person here.
                    expect(screen.getByText(person.name)).toBeInTheDocument();
                    expect(screen.getByText(String(person.yearsOfExperience))).toBeInTheDocument();
                }
                expect(screen.getAllByText("Engineer")).toHaveLength(3);
            });
        });

        it("reports the real row and column count via aria-rowcount/aria-colcount, independent of what is actually mounted", () => {
            renderBasicTable();

            const grid = screen.getByRole("grid", { name: "People" });
            expect(grid).toHaveAttribute("aria-rowcount", String(PEOPLE.length));
            expect(grid).toHaveAttribute("aria-colcount", String(COLUMN_COUNT));
        });
    });

    describe("sorting", () => {
        it("does not render a sort affordance for a column with enableSorting: false", () => {
            renderBasicTable();

            const roleHeader = screen.getByRole("columnheader", { name: "Role" });
            // Omitted entirely, not "none" — `aria-sort` states a *sortable*
            // column's current direction; a column that cannot sort at all has no
            // direction to state, matching the sortable-but-unsorted columns
            // below, which do get an explicit "none".
            expect(roleHeader).not.toHaveAttribute("aria-sort");
            expect(screen.queryByRole("button", { name: "Role" })).not.toBeInTheDocument();
        });

        it("sorts ascending on the first click of a sortable column and reports it via aria-sort", async () => {
            renderBasicTable();
            fireEvent.click(screen.getByRole("button", { name: "Name" }));

            expect(screen.getByRole("columnheader", { name: "Name" })).toHaveAttribute("aria-sort", "ascending");
            await waitFor(() => {
                const cells = screen.getAllByRole("gridcell");
                expect(cellAt(cells, 0, 0)).toHaveTextContent("Ada Lovelace");
                expect(cellAt(cells, 1, 0)).toHaveTextContent("Alan Turing");
                expect(cellAt(cells, 4, 0)).toHaveTextContent("Margaret Hamilton");
            });
        });

        it("sorts descending on the second click, reversing the order", async () => {
            renderBasicTable();
            const nameSortButton = screen.getByRole("button", { name: "Name" });
            fireEvent.click(nameSortButton);
            fireEvent.click(nameSortButton);

            expect(screen.getByRole("columnheader", { name: "Name" })).toHaveAttribute("aria-sort", "descending");
            await waitFor(() => {
                const cells = screen.getAllByRole("gridcell");
                expect(cellAt(cells, 0, 0)).toHaveTextContent("Margaret Hamilton");
                expect(cellAt(cells, 4, 0)).toHaveTextContent("Ada Lovelace");
            });
        });
    });

    describe("keyboard navigation", () => {
        it("starts with exactly one roving tab stop, at the first cell", async () => {
            renderBasicTable();
            const cells = await getGridCells();

            expect(cellAt(cells, 0, 0)).toHaveAttribute("tabindex", "0");
            for (const cell of cells.slice(1)) {
                expect(cell).toHaveAttribute("tabindex", "-1");
            }
        });

        it("ArrowDown/ArrowUp move the active cell (and real DOM focus) between rows in the same column", async () => {
            renderBasicTable();
            const cells = await getGridCells();

            cellAt(cells, 0, 0).focus();
            fireEvent.keyDown(cellAt(cells, 0, 0), { key: "ArrowDown" });
            await waitFor(() => expect(cellAt(cells, 1, 0)).toHaveFocus());
            expect(cellAt(cells, 1, 0)).toHaveAttribute("tabindex", "0");
            expect(cellAt(cells, 0, 0)).toHaveAttribute("tabindex", "-1");

            fireEvent.keyDown(cellAt(cells, 1, 0), { key: "ArrowUp" });
            await waitFor(() => expect(cellAt(cells, 0, 0)).toHaveFocus());
        });

        it("ArrowRight/ArrowLeft move the active cell between columns in the same row", async () => {
            renderBasicTable();
            const cells = await getGridCells();

            cellAt(cells, 2, 0).focus();
            fireEvent.keyDown(cellAt(cells, 2, 0), { key: "ArrowRight" });
            await waitFor(() => expect(cellAt(cells, 2, 1)).toHaveFocus());

            fireEvent.keyDown(cellAt(cells, 2, 1), { key: "ArrowLeft" });
            await waitFor(() => expect(cellAt(cells, 2, 0)).toHaveFocus());
        });

        it("Home and End jump to the first and last column of the current row", async () => {
            renderBasicTable();
            const cells = await getGridCells();

            cellAt(cells, 1, 1).focus();
            fireEvent.keyDown(cellAt(cells, 1, 1), { key: "End" });
            await waitFor(() => expect(cellAt(cells, 1, COLUMN_COUNT - 1)).toHaveFocus());

            fireEvent.keyDown(cellAt(cells, 1, COLUMN_COUNT - 1), { key: "Home" });
            await waitFor(() => expect(cellAt(cells, 1, 0)).toHaveFocus());
        });

        it("clamps at the grid's edges instead of wrapping or crashing", async () => {
            renderBasicTable();
            const cells = await getGridCells();

            cellAt(cells, 0, 0).focus();
            fireEvent.keyDown(cellAt(cells, 0, 0), { key: "ArrowUp" });
            fireEvent.keyDown(cellAt(cells, 0, 0), { key: "ArrowLeft" });
            await waitFor(() => expect(cellAt(cells, 0, 0)).toHaveFocus());

            const lastRow = PEOPLE.length - 1;
            cellAt(cells, lastRow, COLUMN_COUNT - 1).focus();
            fireEvent.keyDown(cellAt(cells, lastRow, COLUMN_COUNT - 1), { key: "ArrowDown" });
            fireEvent.keyDown(cellAt(cells, lastRow, COLUMN_COUNT - 1), { key: "ArrowRight" });
            await waitFor(() => expect(cellAt(cells, lastRow, COLUMN_COUNT - 1)).toHaveFocus());
        });

        it("clicking a cell (not just the keyboard) also moves the roving tab stop to it", async () => {
            renderBasicTable();
            const cells = await getGridCells();

            fireEvent.click(cellAt(cells, 3, 2));
            cellAt(cells, 3, 2).focus();
            await waitFor(() => expect(cellAt(cells, 3, 2)).toHaveAttribute("tabindex", "0"));
        });
    });

    describe("row expansion", () => {
        function renderExpandableTable() {
            return render(
                <DataTable.Root
                    data={PEOPLE}
                    columns={COLUMNS}
                    getRowId={(row) => row.id}
                    aria-label="People"
                    renderExpandedRow={(row) => <p>Notes for {row.name}</p>}
                    expandRowLabel="Expand row"
                    collapseRowLabel="Collapse row"
                >
                    <DataTable.Header />
                    <DataTable.Body />
                </DataTable.Root>,
            );
        }

        it("renders a collapsed toggle per row, named via the caller's own label, with no expanded content queryable yet", async () => {
            renderExpandableTable();

            await waitFor(() => {
                const toggles = screen.getAllByRole("button", { name: "Expand row" });
                expect(toggles).toHaveLength(PEOPLE.length);
                for (const toggle of toggles) expect(toggle).toHaveAttribute("aria-expanded", "false");
            });
            expect(screen.queryByText(/Notes for/)).not.toBeInTheDocument();
        });

        it("expands on click, tracked as this component's own state: shows the caller's content and flips the toggle's label and aria-expanded", async () => {
            renderExpandableTable();
            const [firstToggle] = await waitFor(() => screen.getAllByRole("button", { name: "Expand row" }));
            if (!firstToggle) throw new Error("Expected at least one expand toggle");

            fireEvent.click(firstToggle);

            expect(screen.getByText("Notes for Ada Lovelace")).toBeInTheDocument();
            expect(screen.getByRole("button", { name: "Collapse row" })).toHaveAttribute("aria-expanded", "true");
        });

        it("collapses again on a second click", async () => {
            renderExpandableTable();
            const [firstToggle] = await waitFor(() => screen.getAllByRole("button", { name: "Expand row" }));
            if (!firstToggle) throw new Error("Expected at least one expand toggle");

            // The same DOM node throughout — React reconciles it in place as its
            // own `aria-label`/`aria-expanded` flip, so re-querying by role+name
            // after the first click would (correctly) fail once several rows
            // share the "Expand row" name again.
            fireEvent.click(firstToggle);
            expect(firstToggle).toHaveAttribute("aria-expanded", "true");
            fireEvent.click(firstToggle);

            expect(firstToggle).toHaveAttribute("aria-expanded", "false");
            expect(firstToggle).toHaveAttribute("aria-label", "Expand row");
        });

        it("adds a synthetic leading column for the toggle, reflected in aria-colcount and every row's own column count", () => {
            renderExpandableTable();

            expect(screen.getByRole("grid", { name: "People" })).toHaveAttribute("aria-colcount", String(COLUMN_COUNT + 1));
        });
    });

    describe("layout passthrough", () => {
        it("merges a caller-provided className onto Root's own layout classes", () => {
            renderBasicTable("col-span-2");
            const grid = screen.getByRole("grid", { name: "People" });
            expect(grid).toHaveClass("flex", "flex-col", "col-span-2");
        });
    });
});
