import { Text } from "../text";

export type SpinnerSize = "sm" | "md" | "lg";

export interface SpinnerProps {
    /** @default "md" */
    readonly size?: SpinnerSize;
    /**
     * A caption shown beside the ring for a spinner used on its own (a full
     * PDF render, media processing) — real, visible text, not a
     * screen-reader-only announcement: `role="status"` already announces
     * visible text content to assistive tech the moment it appears, the
     * same way a toast's own visible message does, so hiding it visually
     * bought no accessibility benefit and only cost a sighted user their
     * only clue that something is happening. Omit it when the spinner sits
     * inside an already-labelled busy control — `Button`'s own `aria-busy`
     * on the button element, for instance — where a second announcement
     * would be redundant.
     */
    readonly label?: string;
    readonly className?: string;
}

/**
 * The three sizes read the same `--control-height-*` custom properties
 * `Button`/`IconButton` size their own boxes from — "matching their size
 * vocabulary exactly," per this batch's confirmed decision — but the ring's
 * own diameter is a fraction of that value (`/2.5`) rather than equal to
 * it. A ring literally as tall as its own control cannot fit inside that
 * same control alongside a text label without overflowing it (a `size="sm"`
 * button is only 32px tall in total, most of it spent on padding around the
 * text line) — verified by checking Button's own vertical padding
 * (`py-inline` inside a `h-(--control-height-sm)` box) rather than assumed
 * safe. The ratio was chosen so the "md" step — Button's own default size —
 * lands on 16px, the same step `--control-icon` resolves to, rather than
 * an arbitrary fraction.
 */
const SIZE_DIAMETER: Record<SpinnerSize, string> = {
    sm: "calc(var(--control-height-sm) / 2.5)",
    md: "calc(var(--control-height-md) / 2.5)",
    lg: "calc(var(--control-height-lg) / 2.5)",
};

const BASE_CLASS = "inline-block shrink-0 rounded-pill border-2 border-current border-t-transparent";

/**
 * Tier 0 — a spinning ring for genuinely slow work ("PDF render, media
 * processing" per `COMPONENTS.md`; ordinary optimistic writes show
 * nothing). `border-current` inherits `color` from whatever text context
 * renders it, matching `Icon`'s own still-undecided "no tone, colour
 * inherits" reasoning (`COMPONENTS.md` §13) rather than this component
 * making its own colour decision.
 *
 * The rotation is a `<style>` tag with its own `@keyframes`, the same
 * technique `Skeleton`'s pulse and this component's own predecessor
 * (`Button.tsx`'s inline `LoadingIndicator`, now replaced by this
 * component) already use: the adapter clears `--animate-*` to `initial`, so
 * a theme-keyed `animate-spin` utility would resolve to nothing. Reduced
 * motion is handled by the adapter's existing global rule the same way —
 * see `Skeleton`'s README for the `!important`-beats-inline-style reasoning,
 * identical here.
 */
export function Spinner({ size = "md", label, className }: SpinnerProps) {
    const diameter = SIZE_DIAMETER[size];
    const ring = (
        <span
            aria-hidden={ label ? undefined : "true" }
            style={ { width: diameter, height: diameter, animation: "spinner-spin 0.6s linear infinite" } }
            className={ [BASE_CLASS, label ? undefined : className].filter(Boolean).join(" ") }
        />
    );

    if (!label) {
        return (
            <>
                <style>{ "@keyframes spinner-spin { to { transform: rotate(360deg); } }" }</style>
                { ring }
            </>
        );
    }

    return (
        <>
            <style>{ "@keyframes spinner-spin { to { transform: rotate(360deg); } }" }</style>
            <span role="status"
                  className={ ["inline-flex items-center gap-inline-tight", className].filter(Boolean).join(" ") }>
                { ring }
                <Text variant="small" color="secondary">
                    { label }
                </Text>
            </span>
        </>
    );
}
