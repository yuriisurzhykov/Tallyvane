import { EmptyState } from "frontend-shared/ui/empty-state";

/**
 * Placeholder. Real content: today's recommended actions, per
 * ARCHITECTURE.md §12.5's `today-actions` widget. Exists now only so
 * `app/(console)/today/page.tsx` has a real view to re-export, per this
 * repo's route-file convention — same reasoning as
 * `frontend-admin/src/views/admin-page-list`.
 *
 * No `<main>` landmark here on purpose: that's the future `AppShell`
 * layout's job (Tier 2, not built yet) wrapping every view, not something
 * each view should reinvent — until it exists, this is the view's whole
 * content.
 */
export function TodayPage() {
    return <EmptyState title="Today — coming soon." className="flex h-screen items-center justify-center"/>;
}
