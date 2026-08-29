import type { SidebarNavItem } from "frontend-shared/ui/sidebar-nav";

/**
 * The admin's whole nav (ARCHITECTURE.md §12.2: `pages`, `media`, `strings`
 * — the entire admin surface, no home dashboard). One place rather than
 * one copy per view, so the four screens cannot drift into four slightly
 * different lists of each other.
 *
 * `src/app`, not `src/widgets`: this is composition-root wiring — which
 * routes exist and in what order — not a piece of UI, the same distinction
 * `providers/block-registry.tsx` (§12.6) already draws for this layer.
 */
export function adminNavItems(activeHref: "/pages" | "/media" | "/strings"): SidebarNavItem[] {
    return [
        { label: "Pages", href: "/pages", isActive: activeHref === "/pages" },
        { label: "Media", href: "/media", isActive: activeHref === "/media" },
        { label: "Strings", href: "/strings", isActive: activeHref === "/strings" },
    ];
}
