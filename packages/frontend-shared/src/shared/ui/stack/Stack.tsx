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

export interface StackProps {
    /** No default — forces a deliberate choice of role at every call site. */
    readonly gap: SpacingRole;
    readonly children: ReactNode;
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

/** Tier 0 — vertical flow. Gaps only from the spacing roles; there is no other way to space children apart. */
export function Stack({ gap, children, className }: StackProps) {
    return <div
        className={ ["flex flex-col", GAP_CLASS_NAME[gap], className].filter(Boolean).join(" ") }>{ children }</div>;
}
