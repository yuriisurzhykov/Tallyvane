export interface LogoProps {
    readonly text: string;
    /** Layout and position only, per `COMPONENTS.md` §11 — never a way to override colour or type. */
    readonly className?: string;
}

/**
 * Product wordmark. Tier 0 — see `COMPONENTS.md`'s "Marks and identity" row:
 * single instance, never copied into a feature.
 *
 * `text` is the only way this component ever sees the product name. Per
 * ARCHITECTURE.md §13.4 the name itself lives only in the string dictionary
 * and the build config, never in code — the caller sources it from there,
 * this component just renders whatever it is given.
 */
export function Logo({ text, className }: LogoProps) {
    // v1 placeholder: a real SVG mark will replace this text node. When it
    // does, `text` must become this element's `aria-label` (or feed a
    // `VisuallyHidden` fallback, once that component exists) rather than
    // being removed — the accessible name must survive the visual swap.
    const classes = className ? `text-title3 text-text-primary ${className}` : "text-title3 text-text-primary";
    return <span className={classes}>{text}</span>;
}
