export interface SidebarNavItem {
    readonly label: string;
    readonly href: string;
    readonly isActive: boolean;
}

export interface SidebarNavProps {
    readonly items: readonly SidebarNavItem[];
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

const ITEM_BASE =
    "flex shrink-0 items-center whitespace-nowrap rounded-control px-inline py-inline-tight text-body " +
    "transition-hover outline-none focus-visible:focus-ring lg:whitespace-normal";
const ITEM_ACTIVE = "bg-interactive-primary text-text-on-accent";
const ITEM_INACTIVE = "text-text-secondary hover:bg-surface-row-hover hover:text-text-primary";

/**
 * Tier 2 — the persistent list of destinations beside `AppShell`'s main
 * region (`COMPONENTS.md` §5). Below `lg` it renders as a horizontal,
 * scrollable row instead of the vertical rail `--layout-sidebar-expanded`
 * describes: a usable icon-only collapse needs `Icon`'s own API, which
 * `IconButton.tsx`'s own comment already records as still undecided, and
 * text stays legible at any width where an icon rail would not. `lg` (1024,
 * Tailwind's own default, matching this project's set — see
 * `theme/adapters/tailwind.css`) is where it switches to the full labelled
 * column.
 *
 * Active-item detection is the caller's job, not this component's: `isActive`
 * arrives pre-computed per item so this stays free of any router dependency,
 * the same reasoning `Link.tsx` already applies to navigation.
 */
export function SidebarNav({ items, className }: SidebarNavProps) {
    return (
        <nav
            aria-label="Primary"
            className={[
                "flex flex-row items-center gap-inline-tight overflow-x-auto",
                "lg:w-(--layout-sidebar-expanded) lg:shrink-0 lg:flex-col lg:items-stretch lg:gap-stack-tight lg:overflow-visible",
                className,
            ]
                .filter(Boolean)
                .join(" ")}
        >
            {items.map((item) => (
                <a
                    key={item.href}
                    href={item.href}
                    aria-current={item.isActive ? "page" : undefined}
                    className={[ITEM_BASE, item.isActive ? ITEM_ACTIVE : ITEM_INACTIVE].join(" ")}
                >
                    {item.label}
                </a>
            ))}
        </nav>
    );
}
