import { EmptyState } from "frontend-shared/ui/empty-state";

/**
 * Placeholder. Real content: the list of pages with draft/published state,
 * per ARCHITECTURE.md §12.9 — "Список страниц с состоянием черновика и
 * публикации." Exists now only so `app/(admin)/pages/page.tsx` has a real
 * view to re-export, per this repo's route-file convention.
 *
 * No `<main>` landmark here on purpose: that's the future `AppShell` layout's
 * job (Tier 2, not built yet) wrapping every view, not something each view
 * should reinvent — until it exists, this is the view's whole content.
 */
export function AdminPageListView() {
    return <EmptyState title="Pages — coming soon." className="flex h-screen items-center justify-center"/>;
}
