import type { ReactNode } from "react";
import { Text } from "../../text";

export interface TopBarProps {
    /** The current screen's name — one line, no document heading semantics of its own (see `Text`'s own reasoning). */
    readonly title: string;
    /** Search, account, whatever a screen needs on the right. Omit for a bare title bar. */
    readonly actions?: ReactNode;
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

/**
 * Tier 2 — the strip above `AppShell`'s main region (`COMPONENTS.md` §5).
 * Two slots only: a title, always present, and an optional trailing
 * `actions` region. Search, theme and density controls the full spec names
 * are `actions` content a caller supplies, not this component's business —
 * none of today's call sites need them yet (YAGNI), and adding one later is
 * a new element in that slot, not a new prop here.
 */
export function TopBar({ title, actions, className }: TopBarProps) {
    return (
        <header
            className={[
                "flex flex-row items-center justify-between gap-inline border-b border-border-subtle px-stack py-inline-tight",
                className,
            ]
                .filter(Boolean)
                .join(" ")}
        >
            <Text variant="title3" render={<h1 />}>
                {title}
            </Text>
            {actions !== undefined ? <div className="flex flex-row items-center gap-inline-tight">{actions}</div> : null}
        </header>
    );
}
