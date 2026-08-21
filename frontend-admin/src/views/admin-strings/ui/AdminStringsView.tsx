import { EmptyState } from "frontend-shared/ui/empty-state";

/**
 * Placeholder. Real content: editable strings grouped by namespace, default
 * value shown alongside, per ARCHITECTURE.md §12.9 and §13.3.
 *
 * No `<main>` landmark here on purpose: that's the future `AppShell` layout's
 * job (Tier 2, not built yet) wrapping every view, not something each view
 * should reinvent — until it exists, this is the view's whole content.
 */
export function AdminStringsView() {
    return <EmptyState title="Strings — coming soon." />;
}
