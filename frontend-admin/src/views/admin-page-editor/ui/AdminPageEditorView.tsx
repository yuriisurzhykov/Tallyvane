import { EmptyState } from "frontend-shared/ui/empty-state";

/**
 * Placeholder. Real content: the three-column block editor — blocks,
 * generated field form, live preview — per ARCHITECTURE.md §12.9. Will use
 * `content-kit`'s `block-editor` widget once it exists.
 *
 * No `<main>` landmark here on purpose: that's the future `AppShell` layout's
 * job (Tier 2, not built yet) wrapping every view, not something each view
 * should reinvent — until it exists, this is the view's whole content.
 */
export function AdminPageEditorView() {
    return <EmptyState title="Page editor — coming soon." className="w-max flex items-center justify-center"/>;
}
