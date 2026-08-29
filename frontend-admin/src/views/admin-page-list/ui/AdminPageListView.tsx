"use client";

import { AppShell } from "frontend-shared/ui/app-shell";
import { DataTable, type DataTableColumnDef } from "frontend-shared/ui/data-table";
import { Badge, type BadgeTone } from "frontend-shared/ui/badge";
import { adminNavItems } from "@/app/navigation";

type PageStatus = "draft" | "published";

interface AdminPage {
    readonly id: string;
    readonly title: string;
    readonly slug: string;
    readonly status: PageStatus;
    readonly updatedAt: string;
}

const STATUS_TONE: Record<PageStatus, BadgeTone> = { draft: "neutral", published: "success" };

/**
 * Static for now — no CMS content API exists yet to list real pages from.
 * See `README.md`'s dated entry.
 */
const MOCK_PAGES: AdminPage[] = [
    { id: "1", title: "Landing", slug: "/", status: "published", updatedAt: "2026-08-20" },
    { id: "2", title: "About", slug: "/about", status: "draft", updatedAt: "2026-08-25" },
    { id: "3", title: "Changelog — v0.3", slug: "/changelog", status: "published", updatedAt: "2026-08-27" },
];

const COLUMNS: DataTableColumnDef<AdminPage>[] = [
    { accessorKey: "title", header: "Title" },
    { accessorKey: "slug", header: "Slug", enableSorting: false },
    {
        accessorKey: "status",
        header: "Status",
        cell: (info) => <Badge tone={STATUS_TONE[info.getValue<PageStatus>()]}>{info.getValue<PageStatus>()}</Badge>,
    },
    { accessorKey: "updatedAt", header: "Updated" },
];

/**
 * Wired to `AppShell` and a real `DataTable` on 2026-08-28, replacing the
 * `EmptyState` placeholder — per ARCHITECTURE.md §12.9's page-list screen
 * (draft/published).
 */
export function AdminPageListView() {
    return (
        <AppShell navItems={adminNavItems("/pages")} title="Pages" skipLinkLabel="Skip to content">
            <DataTable.Root data={MOCK_PAGES} columns={COLUMNS} getRowId={(row) => row.id} aria-label="Pages">
                <DataTable.Header />
                <DataTable.Body />
            </DataTable.Root>
        </AppShell>
    );
}
