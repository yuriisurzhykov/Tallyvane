import type { SidebarNavItem } from "frontend-shared/ui/sidebar-nav";

/** Only destinations that currently exist are exposed in the console navigation. */
export function appNavItems(active: "today" | "settings"): SidebarNavItem[] {
    return [
        { label: "Today", href: "/today", isActive: active === "today" },
        { label: "Settings", href: "/account/profile", isActive: active === "settings" },
    ];
}
