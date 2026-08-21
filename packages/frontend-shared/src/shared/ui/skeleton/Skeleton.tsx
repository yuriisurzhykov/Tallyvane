import type { CSSProperties } from "react";

export interface SkeletonProps {
    /** Layout and sizing — see `COMPONENTS.md` §11. A skeleton has no intrinsic shape of its own; it always mimics whatever real content it is standing in for. */
    readonly className?: string;
    /** For a caller-specific size the token scale has no role for (e.g. matching a real column's width) — the same escape hatch `Drawer`'s width and `SearchField`'s `ICON_INSET` already use. */
    readonly style?: CSSProperties;
}

/**
 * A `<div>` has no intrinsic height without content or an explicit one, and
 * `Skeleton` renders none — without a default it would be invisible out of
 * the box. `h-stack` (one `dimension.4`/16px step) approximates a single
 * line of body text, the same way `Dot`'s own diameter borrows a spacing
 * role for a non-gap geometry value rather than inventing a raw pixel
 * (`size-inline`). `w-full` matters for the same reason inside a flex or
 * grid row: with no content of its own, a skeleton with no explicit width
 * collapses to zero there. Both are fully overridable via `className`.
 */
const BASE_CLASS = "h-stack w-full rounded-control bg-surface-inset";

/**
 * Tier 0 — a loading placeholder. Pulses via a real CSS `animation` (a
 * `<style>` tag with its own `@keyframes`, not Tailwind's `animate-pulse`
 * utility) for the same reason `Button.tsx`'s own `LoadingIndicator` uses
 * one: the adapter clears `--animate-*` to `initial`, so a theme-keyed
 * `animate-pulse` class would silently resolve to nothing.
 *
 * Stays visually still under `prefers-reduced-motion` through the adapter's
 * OWN global rule (`theme/adapters/tailwind.css`'s `@media
 * (prefers-reduced-motion: reduce)` block, which forces `animation-duration:
 * 1ms` and `animation-iteration-count: 1` on every element, `!important`) —
 * verified against that rule rather than adding a second, local media query,
 * per `COMPONENTS.md` §12 ("reduced motion is handled globally"). An
 * `!important` author rule beats a plain inline `style` declaration for the
 * same property regardless of specificity, so this holds even though the
 * animation itself is set inline.
 *
 * `aria-hidden="true"`, always: a skeleton is purely decorative. The
 * "loading" announcement itself belongs to whatever composes several of
 * these into a real loading state (`LoadingRegion`, Tier 1, not built yet) —
 * this primitive does not know it is one of several, or what it is
 * replacing.
 */
export function Skeleton({ className, style }: SkeletonProps) {
    return (
        <>
            <style>{ "@keyframes skeleton-pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }" }</style>
            <div
                aria-hidden="true"
                className={ [BASE_CLASS, className].filter(Boolean).join(" ") }
                style={ { animation: "skeleton-pulse 1.5s ease-in-out infinite", ...style } }
            />
        </>
    );
}
