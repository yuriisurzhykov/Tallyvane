import type { ReactNode } from "react";

export type SurfaceVariant = "primary" | "elevated" | "inset";

const SURFACE_VARIANT_CLASS_NAME: Record<SurfaceVariant, string> = {
    primary: "bg-surface-primary",
    elevated: "bg-surface-elevated",
    inset: "bg-surface-inset",
};

export interface SurfaceProps {
    /** @default "primary" */
    readonly variant?: SurfaceVariant;
    readonly children: ReactNode;
    /** Layout and position only — see `COMPONENTS.md` §11. Never colour: that is what `variant` is for. */
    readonly className?: string;
}

/**
 * Tier 0 — a themed background with a border. The card in the page flow.
 *
 * No shadow, ever: per `composites/shadows.ts`, elevation is reserved for
 * things that float above content they did not lay out (overlays), and a
 * surface sitting in normal flow is never one of those.
 */
export function Surface({ variant = "primary", children, className }: SurfaceProps) {
    return (
        <div
            className={[SURFACE_VARIANT_CLASS_NAME[variant], "border border-border-subtle rounded-card", className]
                .filter(Boolean)
                .join(" ")}
        >
            {children}
        </div>
    );
}
