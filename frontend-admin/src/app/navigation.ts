import { enDictionary } from "@/shared/i18n";
import type { NavGroup } from "@/widgets/admin-shell";

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

export function adminNavGroups(activeHref: AdminHref): NavGroup[] {
    const words = enDictionary.nav;
    const item = (label: string, href: AdminHref) => ({ label, href, isActive: activeHref === href });
    return [
        { label: words.groupContent, items: [item(words.pages, "/pages"), item(words.media, "/media"), item(words.strings, "/strings")] },
        { label: words.groupSignIn, items: [item(words.policies, "/policies"), item(words.lifetimes, "/lifetimes")] },
    ];
}
