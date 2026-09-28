import type { MouseEvent } from "react";
import { Text } from "../../text";

export interface SidebarNavItem {
    readonly label: string;
    readonly href: string;
    readonly isActive: boolean;
}

export type SidebarNavLayout = "responsive" | "vertical";
export type SidebarNavActiveAppearance = "primary" | "subtle";
export type SidebarNavSurface = "transparent" | "inset";

export interface SidebarNavProps {
    readonly items: readonly SidebarNavItem[];
    readonly ariaLabel?: string;
    readonly heading?: string;
    readonly layout?: SidebarNavLayout;
    readonly activeAppearance?: SidebarNavActiveAppearance;
    readonly surface?: SidebarNavSurface;
    readonly onNavigate?: (href: string, event: MouseEvent<HTMLAnchorElement>) => void;
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

const ITEM_BASE =
    "flex shrink-0 items-center rounded-control px-inline py-inline-tight text-body " +
    "transition-hover outline-none focus-visible:focus-ring";
const ITEM_ACTIVE_PRIMARY = "bg-interactive-primary text-text-on-accent";
const ITEM_ACTIVE_SUBTLE = "bg-interactive-primary-subtle text-interactive-primary-text font-semibold";
const ITEM_INACTIVE = "text-text-secondary hover:bg-surface-row-hover hover:text-text-primary";

/**
 * Tier 2 — the persistent list of destinations beside `AppShell`'s main
 * region (`COMPONENTS.md` §5). The default responsive layout renders a
 * horizontal, scrollable row below `lg` and a vertical column above it.
 * `vertical` is for nested workspaces whose host owns the mobile interaction,
 * such as the settings workspace's left drawer.
 *
 * Active-item detection is the caller's job, not this component's: `isActive`
 * arrives pre-computed per item so this stays free of any router dependency,
 * the same reasoning `Link.tsx` already applies to navigation.
 */
export function SidebarNav({
    items,
    ariaLabel = "Primary",
    heading,
    layout = "responsive",
    activeAppearance = "primary",
    surface = "transparent",
    onNavigate,
    className,
}: SidebarNavProps) {
    const layoutClassName = layout === "vertical"
        ? "flex w-full flex-col items-stretch gap-stack-tight overflow-visible"
        : "flex flex-row items-center gap-inline-tight overflow-x-auto lg:w-(--layout-sidebar-expanded) lg:shrink-0 lg:flex-col lg:items-stretch lg:gap-stack-tight lg:overflow-visible";
    const surfaceClassName = surface === "inset" ? "self-stretch bg-surface-inset p-stack" : "";
    const activeClassName = activeAppearance === "subtle" ? ITEM_ACTIVE_SUBTLE : ITEM_ACTIVE_PRIMARY;

    return (
        <nav
            aria-label={ariaLabel}
            className={[
                layoutClassName,
                surfaceClassName,
                className,
            ]
                .filter(Boolean)
                .join(" ")}
        >
            {heading ? <Text variant="overline" color="muted">{heading}</Text> : null}
            {items.map((item) => (
                <a
                    key={item.href}
                    href={item.href}
                    aria-current={item.isActive ? "page" : undefined}
                    className={[
                        ITEM_BASE,
                        layout === "vertical" ? "w-full whitespace-normal" : "shrink-0 whitespace-nowrap lg:whitespace-normal",
                        item.isActive ? activeClassName : ITEM_INACTIVE,
                    ].join(" ")}
                    onClick={onNavigate ? event => { onNavigate(item.href, event); } : undefined}
                >
                    {item.label}
                </a>
            ))}
        </nav>
    );
}
