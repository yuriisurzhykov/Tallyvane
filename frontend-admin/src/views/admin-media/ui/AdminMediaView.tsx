import { EmptyState } from "frontend-shared/ui/empty-state";

/**
 * Placeholder. Real content: the media library with alt text and usage
 * locations, per ARCHITECTURE.md §12.9.
 *
 * No `<main>` landmark here on purpose: that's the future `AppShell` layout's
 * job (Tier 2, not built yet) wrapping every view, not something each view
 * should reinvent — until it exists, this is the view's whole content.
 */
export function AdminMediaView() {
    return <EmptyState title="Media — coming soon." />;
}
