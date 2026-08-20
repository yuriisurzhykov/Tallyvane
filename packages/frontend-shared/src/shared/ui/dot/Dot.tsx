import { VisuallyHidden } from "../visually-hidden";

export type DotTone = "neutral" | "info" | "attention" | "success" | "danger";

export interface DotProps {
    /** A dot with no meaning is pointless — there is no ambient default. */
    readonly tone: DotTone;
    /**
     * An accessible name for the dot, wired through `VisuallyHidden`. Only
     * needed when the dot conveys meaning with no adjacent text; when it
     * accompanies a label that already says the same thing, omit it and the
     * dot is marked `aria-hidden`.
     */
    readonly label?: string;
    readonly className?: string;
}

/**
 * Backgrounds come from each tone's TEXT colour role, not its fill role —
 * a fill is deep enough to carry white text on top of it, which makes it
 * nearly invisible as a small dot on a dark page. `neutral` has no status
 * role of its own, so it borrows the `text-muted` role the same way
 * `.cursor/skills/component-authoring/patterns.md`'s own `toneToRole`
 * example does for a `Tone` union with no dedicated "neutral" colour.
 */
const TONE_CLASS: Record<DotTone, string> = {
    neutral: "bg-text-muted",
    info: "bg-status-info-text",
    attention: "bg-status-attention-text",
    success: "bg-status-success-text",
    danger: "bg-status-danger-text",
};

/** The status dot: a small filled circle carrying a `tone`. The other round thing, besides `Avatar`. */
export function Dot({ tone, label, className }: DotProps) {
    const classNames = `inline-block size-inline rounded-pill ${ TONE_CLASS[tone] }${ className ? ` ${ className }` : "" }`;

    return (
        <span className={ classNames } { ...(label ? {} : { "aria-hidden": "true" as const }) }>
            { label ? <VisuallyHidden>{ label }</VisuallyHidden> : null }
        </span>
    );
}
