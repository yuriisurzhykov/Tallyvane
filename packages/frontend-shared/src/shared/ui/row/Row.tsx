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

export interface RowProps {
    /** No default — forces a deliberate choice of role at every call site. */
    readonly gap: SpacingRole;
    readonly children: ReactNode;
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

/**
 * Tier 0 — horizontal flow, centre-aligned on the cross axis by default: the
 * common case is an icon beside a label, where the two need a shared
 * baseline-ish middle rather than each sitting at its own top edge.
 */
export function Row({ gap, children, className }: RowProps) {
    return (
        <div className={ ["flex flex-row items-center", GAP_CLASS_NAME[gap], className].filter(Boolean).join(" ") }>
            { children }
        </div>
    );
}
