import type { ReactNode } from "react";
import { SidebarNav, type SidebarNavItem } from "../sidebar-nav";
import { TopBar } from "../top-bar";
import { SkipLink } from "../../skip-link";

const MAIN_CONTENT_ID = "main-content";

export interface AppShellProps {
    readonly navItems: readonly SidebarNavItem[];
    /** The current screen's name, forwarded to `TopBar`'s own `<h1>`. */
    readonly title: string;
    /** `SkipLink`'s visible-on-focus label — a prop, not hardcoded here (`COMPONENTS.md` §12: copy belongs to the caller below Tier 3). */
    readonly skipLinkLabel: string;
    /** Forwarded to `TopBar`. Omit for a bare title bar. */
    readonly actions?: ReactNode;
    /** The active screen. */
    readonly children: ReactNode;
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

/**
 * Tier 2 — sidebar plus main region, the composition root every console and
 * admin screen renders inside (`COMPONENTS.md` §5). Owns the `#main-content`
 * landmark `SkipLink.tsx`'s own comment already named as a future `AppShell`
 * responsibility, so this is the one place that decision actually lands.
 *
 * Stacks vertically below `lg` (`SidebarNav` becomes the horizontal row it
 * already is at that width) and sits side by side from `lg` up — one
 * breakpoint switch, not two layouts maintained separately.
 */
export function AppShell({ navItems, title, skipLinkLabel, actions, children, className }: AppShellProps) {
    return (
        <div className={["flex min-h-dvh flex-col lg:flex-row", className].filter(Boolean).join(" ")}>
            <SkipLink href={`#${MAIN_CONTENT_ID}`}>{skipLinkLabel}</SkipLink>
            <SidebarNav
                items={navItems}
                className="border-b border-border-subtle p-inline-tight lg:border-r lg:border-b-0 lg:p-stack"
            />
            <div className="flex min-w-0 flex-1 flex-col">
                <TopBar title={title} {...(actions !== undefined ? { actions } : {})} />
                <main id={MAIN_CONTENT_ID} className="flex-1 overflow-y-auto p-stack">
                    {children}
                </main>
            </div>
        </div>
    );
}
