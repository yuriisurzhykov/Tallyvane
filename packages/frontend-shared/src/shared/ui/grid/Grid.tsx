import type { ReactNode } from "react";

export type SpacingRole = "inline-tight" | "inline" | "stack-tight" | "stack" | "group-gap" | "section-gap";

const GAP_CLASS_NAME: Record<SpacingRole, string> = {
    "inline-tight": "gap-inline-tight",
    inline: "gap-inline",
    "stack-tight": "gap-stack-tight",
    stack: "gap-stack",
    "group-gap": "gap-group-gap",
    "section-gap": "gap-section-gap",
};

export interface GridProps {
    readonly columns: number;
    /** No default — forces a deliberate choice of role at every call site. */
    readonly gap: SpacingRole;
    readonly children: ReactNode;
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

/**
 * Tier 0 — column layout with token gaps.
 *
 * `columns` drives an inline `grid-template-columns`: there is no spacing (or
 * any other) token for an arbitrary column count, so unlike `gap` — which
 * must resolve to a registered `gap-<role>` class — this one number is the
 * one acceptable inline style here.
 */
export function Grid({ columns, gap, children, className }: GridProps) {
    return (
        <div
            className={["grid", GAP_CLASS_NAME[gap], className].filter(Boolean).join(" ")}
            style={{ gridTemplateColumns: `repeat(${ String(columns) }, minmax(0, 1fr))` }}
        >
            {children}
        </div>
    );
}
