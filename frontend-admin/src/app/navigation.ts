import type { SidebarNavItem } from "frontend-shared/ui/sidebar-nav";
import { enDictionary } from "@/shared/i18n";

/**
 * The admin's whole nav (ARCHITECTURE.md §12.2: the CMS screens and the two screens that govern
 * sign-in, no home dashboard). One place rather than one copy per view, so the screens cannot drift
 * into slightly different lists of each other.
 *
 * `src/app`, not `src/widgets`: this is composition-root wiring — which
 * routes exist and in what order — not a piece of UI, the same distinction
 * `providers/block-registry.tsx` (§12.6) already draws for this layer.
 */
export type AdminHref = "/pages" | "/media" | "/strings" | "/policies" | "/lifetimes";

export function adminNavItems(activeHref: AdminHref): SidebarNavItem[] {
    const words = enDictionary.nav;
    return [
        { label: words.pages, href: "/pages", isActive: activeHref === "/pages" },
        { label: words.media, href: "/media", isActive: activeHref === "/media" },
        { label: words.strings, href: "/strings", isActive: activeHref === "/strings" },
        { label: words.policies, href: "/policies", isActive: activeHref === "/policies" },
        { label: words.lifetimes, href: "/lifetimes", isActive: activeHref === "/lifetimes" },
    ];
}
