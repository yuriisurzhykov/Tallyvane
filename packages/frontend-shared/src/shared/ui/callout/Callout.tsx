import type { ReactNode } from "react";

export type CalloutTone = "neutral" | "info" | "attention" | "success" | "danger";

export interface CalloutProps {
    /** A callout with no tone is unstyled by accident, not by choice — no ambient default, same reasoning as `Dot`'s `tone`. */
    readonly tone: CalloutTone;
    /**
     * A generic slot, not typed against `Icon`'s own (still-undecided) API —
     * the same reasoning `Button`'s `leadingIcon` already uses. Omit it for
     * a text-only callout.
     */
    readonly leadingIcon?: ReactNode;
    /** The explanation. */
    readonly children: ReactNode;
    readonly className?: string;
}

/**
 * Wash plus a coloured left accent — the same "toned block" language
 * `ToastRegion.tsx` already established, but with the wash Toast's own
 * comment reserved for `Badge` instead of Toast's own border-only look: a
 * callout is read in place, in the page's own flow, not glanced at over
 * page content the way a floating toast is, so it earns the fuller
 * treatment. `neutral` again has no status wash, so it borrows `Surface`'s
 * own recessed `inset` variant and the shared `border-default` role instead
 * of a status pair that does not exist for it.
 */
const TONE_CLASS: Record<CalloutTone, string> = {
    neutral: "border-border-default bg-surface-inset text-text-muted",
    info: "border-status-info bg-status-info-subtle text-status-info-text",
    attention: "border-status-attention bg-status-attention-subtle text-status-attention-text",
    success: "border-status-success bg-status-success-subtle text-status-success-text",
    danger: "border-status-danger bg-status-danger-subtle text-status-danger-text",
};

/**
 * `items-center`, not the flex default (`stretch`) — a real, measured bug:
 * without it, the icon's wrapping `<span>` stretched to the row's full
 * height while the icon glyph inside it stayed pinned to the top, reading
 * as visibly misaligned against even a single line of text.
 */
const BASE_CLASS = "flex items-center gap-inline rounded-card border-l-4 p-stack text-body";

/**
 * Tier 0 — an always-persistent toned block of explanation (tax warnings,
 * the LLM budget notice, extraction-confidence caveats). No dismiss
 * affordance: unlike `Toast`, a callout is part of the content it
 * annotates, not a transient notification about an event that already
 * happened. `role="note"` per the ARIA definition of a section carrying
 * supplementary information that is not itself the main content flow.
 */
export function Callout({ tone, leadingIcon, children, className }: CalloutProps) {
    const toneClass = TONE_CLASS[tone];
    const classNames = `${ BASE_CLASS } ${ toneClass }${ className ? ` ${ className }` : "" }`;

    return (
        <div role="note" className={ classNames }>
            { leadingIcon ? (
                <span aria-hidden="true" className="shrink-0">
                    { leadingIcon }
                </span>
            ) : null }
            <div className="flex-1 text-text-primary">{ children }</div>
        </div>
    );
}
