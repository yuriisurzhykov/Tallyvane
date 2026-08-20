import type { ReactNode } from "react";

export type BadgeTone = "neutral" | "info" | "attention" | "success" | "danger";
export type BadgeTreatment = "solid" | "subtle";

export interface BadgeProps {
    /** A badge with no tone is unstyled by accident, not by choice — no ambient default, same reasoning as `Dot`'s `tone`. */
    readonly tone: BadgeTone;
    /** @default "subtle" */
    readonly treatment?: BadgeTreatment;
    /** The label. */
    readonly children: ReactNode;
    readonly className?: string;
}

/**
 * Deep fill + light text, the same pairing `themes/shared-roles.ts` already
 * tunes the four status colours for ("dark fills with white text clear the
 * bar with room to spare"). `neutral` has no status fill of its own, so it
 * borrows the monochrome accent pair `Button`'s `primary` tone already uses
 * (`interactivePrimary`/`textOnAccent`) — deliberately tuned to hold
 * contrast in both themes the same way the status pairs are, unlike
 * `textPrimary`, which is near-white in the dark theme and would be
 * illegible under white text.
 */
const SOLID_CLASS: Record<BadgeTone, string> = {
    neutral: "bg-interactive-primary text-text-on-accent",
    info: "bg-status-info text-text-on-solid",
    attention: "bg-status-attention text-text-on-solid",
    success: "bg-status-success text-text-on-solid",
    danger: "bg-status-danger text-text-on-solid",
};

/**
 * Wash plus matching text — the treatment `ToastRegion.tsx`'s own comment
 * already named as "Badge's own planned tone treatment," kept distinct from
 * Toast's own (border-accent-only) look since a toast is read over the page
 * behind it while a badge sits inline as a label. `neutral` again has no
 * status wash, so it borrows `Dot`'s neutral fallback (`text-muted`'s
 * sibling `text-secondary`, for a full word rather than a 4px dot) over a
 * plain recessed surface.
 */
const SUBTLE_CLASS: Record<BadgeTone, string> = {
    neutral: "bg-surface-inset text-text-secondary",
    info: "bg-status-info-subtle text-status-info-text",
    attention: "bg-status-attention-subtle text-status-attention-text",
    success: "bg-status-success-subtle text-status-success-text",
    danger: "bg-status-danger-subtle text-status-danger-text",
};

/**
 * Reads `statusBadgeTokens` directly via Tailwind's bracket-free
 * custom-property syntax — the same mechanism `Button.tsx`'s own
 * `h-(--control-height-md)` uses for a role that intentionally has no
 * generated bare utility (`COMPONENTS.md` §12: a badge's tightness belongs
 * to the badge, not the shared spacing scale). No `gap`/icon slot: this
 * batch's confirmed API is `tone` plus `solid`/`subtle` only, and
 * `statusBadgeTokens.dotSize` — real, but unconsumed here — stays reserved
 * for a possible future `ApplicationStatusBadge` fused dot+label treatment
 * rather than an unrequested `leadingDot` prop on this component (YAGNI:
 * no call site asks for it yet).
 */
const BASE_CLASS =
    "inline-flex items-center whitespace-nowrap rounded-(--ds-component-status-badge-radius) px-(--ds-component-status-badge-padding-x) py-(--ds-component-status-badge-padding-y) text-small";

/** Tier 0 — a status label: `tone` resolved to a background/text pair, `treatment` choosing wash versus fill. No behaviour, no Base UI primitive. */
export function Badge({ tone, treatment = "subtle", children, className }: BadgeProps) {
    const treatmentClass = treatment === "solid" ? SOLID_CLASS[tone] : SUBTLE_CLASS[tone];
    const classNames = `${ BASE_CLASS } ${ treatmentClass }${ className ? ` ${ className }` : "" }`;

    return <span className={ classNames }>{ children }</span>;
}
