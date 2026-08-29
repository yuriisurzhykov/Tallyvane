"use client";

import { Root } from "./Root";
import { Header } from "./Header";
import { Body } from "./Body";
import { Row } from "./Row";
import { Cell } from "./Cell";

export type { DataTableColumnDef } from "./table-features";
export type {
    DataTableBodyProps,
    DataTableCellProps,
    DataTableHeaderProps,
    DataTableRootProps,
    DataTableRowProps,
} from "./DataTable.types";

/**
 * Tier 1 — the single most load-bearing component in the product
 * (`COMPONENTS.md` §4: "the pipeline is a thousand rows at 16 ms a frame").
 * `Root`/`Header`/`Body`/`Row`/`Cell` (each its own file in this directory),
 * backed by `@tanstack/react-table` and `@tanstack/react-virtual` end to end
 * (`ADR-031`'s own named gap: a virtualized table is headless, and Base UI
 * does not cover it). Every part below `Root` reads the `state`/`actions`/
 * `meta` context contract (`patterns.md` §2, `data-table-context.ts`) — none
 * of them calls `useTable`/`useVirtualizer` directly.
 */
export const DataTable = { Root, Header, Body, Row, Cell };
