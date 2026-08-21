import type { ReactNode } from "react";
import { X } from "lucide-react";
import { CONTROL_ICON_CLASS } from "../../lib";

export type TagTone = "neutral" | "info" | "attention" | "success" | "danger";

export interface TagProps {
    /** @default "neutral" */
    readonly tone?: TagTone;
    /** The content — a tech tag, a skill, or any other short removable value. */
    readonly children: ReactNode;
    /** Fires when the dismiss affordance is activated. Required: a `Tag` with no way to remove it is a `Badge`. */
    readonly onRemove: () => void;
    /** Accessible name for the dismiss button — Tier 0 owns no copy of its own, the same reasoning `SearchField`'s `clearLabel` already follows. */
    readonly removeLabel: string;
    readonly className?: string;
}

/**
 * Wash plus a real border, one step past `Badge`'s own `subtle` treatment:
 * a tag is a removable, focusable object hosting its own interactive
 * affordance, not a passive label, and the border is what reads "this has
 * an edge you can act on" rather than "this is a coloured word." `neutral`
 * again has no status pair, so it borrows `Surface`'s recessed `inset` look
 * plus the shared `border-default` role.
 */
const TONE_CLASS: Record<TagTone, string> = {
    neutral: "border-border-default bg-surface-inset text-text-secondary",
    info: "border-status-info bg-status-info-subtle text-status-info-text",
    attention: "border-status-attention bg-status-attention-subtle text-status-attention-text",
    success: "border-status-success bg-status-success-subtle text-status-success-text",
    danger: "border-status-danger bg-status-danger-subtle text-status-danger-text",
};

/**
 * `rounded-chip`, not `Badge`'s `pill` — a squarer corner for a removable
 * object you interact with rather than a status word (`semantic/radius.ts`:
 * `chip` is the smallest of the five roles, `pill` the roundest). Padding
 * comes from the same shared `inline`/`inline-tight` spacing roles every
 * other Tier 0 primitive already reads directly, not a dedicated
 * component-token file.
 *
 * A `tagTokens` component-token file (mirroring `statusBadgeTokens`) was
 * the first draft here, per this batch's confirmed decision to give `Tag`
 * "its own component tokens, distinct from `statusBadgeTokens`." Building
 * it surfaced a real, machine-checked objection: the token compiler's
 * DS201 rule failed the build the moment both `statusBadgeTokens` and a new
 * `tagTokens` referenced the same raw `dimension.1`/`dimension.2`
 * primitives independently — "a primitive crosses component/composite
 * domain boundaries... promote to a global-semantic role, OR keep both
 * independent if this is coincidence, not shared meaning." It was not a
 * coincidence: both components want "tight label padding," which is
 * exactly what a shared semantic role is for, and `inline`/`inline-tight`
 * already exist as required roles serving that job everywhere else in this
 * package. Picking artificially different raw numbers to dodge the check
 * would have been the coincidence, not the fix — so the token file was
 * deleted (see `git log`/this batch's report for the reverted diff) in
 * favour of the two roles Tag now reads directly, and `chip` covers the
 * one dimension (`COMPONENTS.md`'s confirmed "not pill") that genuinely is
 * a Tag-specific choice, without needing a component-token indirection to
 * express it — it is already a first-class semantic role.
 */
const BASE_CLASS =
    "inline-flex items-center gap-inline-tight whitespace-nowrap rounded-chip border px-inline py-inline-tight text-small";

const REMOVE_BUTTON_CLASS = "rounded-control p-inline-tight text-current opacity-70 transition-hover hover:opacity-100 focus-visible:focus-ring";

/**
 * Tier 0 — a removable chip: a tech tag or skill, and what `MultiSelect`
 * (a later batch) renders each selected value as. Hand-rolled, not
 * `IconButton`, for the dismiss affordance: `IconButton` is a square,
 * `control`-height-sized real button (32px at its smallest) — the right
 * weight for a toggle sitting beside a field, but heavier than a chip only
 * ~24px tall can hold without the button dominating it. Follows
 * `SearchField.tsx`'s own precedent instead — a real `<button
 * type="button">`, a required accessible name, sized to keep the WCAG 2.2
 * 24×24 CSS px hit target this project's own component-authoring skill
 * calls for (a 16px glyph plus `p-inline-tight` on every side).
 */
export function Tag({ tone = "neutral", children, onRemove, removeLabel, className }: TagProps) {
    const classNames = `${ BASE_CLASS } ${ TONE_CLASS[tone] }${ className ? ` ${ className }` : "" }`;

    return (
        <span className={ classNames }>
            { children }
            <button type="button" aria-label={ removeLabel } onClick={ onRemove } className={ REMOVE_BUTTON_CLASS }>
                <X aria-hidden="true" className={CONTROL_ICON_CLASS}/>
            </button>
        </span>
    );
}
