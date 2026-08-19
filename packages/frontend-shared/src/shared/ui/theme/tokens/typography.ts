import { definePrimitives } from "design-token-engine";

/**
 * Type primitives.
 *
 * Sizes are a numbered scale rather than a set of role names, and the reason is
 * that a scale has to be readable as one: 32 / 24 / 20 / 17 / 16 / 14 / 13 / 11
 * can be scanned in a column, and a gap or an accidental duplicate shows up
 * immediately. Role-named sizes hide that — nothing tells you whether two of
 * them are adjacent steps or the same number twice.
 *
 * It also lets styles share a step honestly. `body` and `bodyStrong` differ
 * only in weight; with named sizes one of them would have to reference a size
 * named after the other, and the name would then be lying about who uses it.
 * The role names live one layer up, on the finished styles in
 * `composites/text-styles.ts`, which is what a component actually applies.
 *
 * Size and line are indexed in lockstep: step 4's size goes with step 4's line.
 * They are absolute lengths rather than ratios because the pairs come from a
 * reviewed table (§8) where each was chosen against the other, and a ratio
 * would quietly re-derive one of them.
 *
 * Sizes are fixed, not fluid. `clamp()` earns its complexity on a page spanning
 * phone to billboard; this is a console with a bounded content width, where a
 * heading that resizes with the viewport only makes two windows side by side
 * disagree.
 *
 * Specification: docs/frontend/01-shared-design-tokens.md §8.
 */
export const typography = definePrimitives({
    /**
     * `var(--font-ibm-plex-sans)`, not a quoted family name. Those custom
     * properties are set on `<html>` by `next/font/google` (see
     * `app/fonts.ts`), and their value already carries Next's generated
     * fallback chain — a metric-matched face first, then the system default —
     * so the real font and its stand-in are the same size and swapping between
     * them shifts nothing.
     *
     * Naming the families as quoted strings instead is the failure to guard
     * against: nothing loads them, every page renders in the system fallback,
     * and no error is raised anywhere. The stack after the variable is the last
     * resort for when the layout has not applied `.variable` at all.
     */
    family: {
        sans: "var(--font-ibm-plex-sans), system-ui, sans-serif",
        mono: "var(--font-ibm-plex-mono), ui-monospace, SFMono-Regular, monospace",
    },

    /**
     * Stops at 700, which is where IBM Plex Sans stops. Beyond it the browser
     * fabricates the weight by thickening the outlines, and the result reads as
     * a slightly blurred bold rather than a heavier face.
     */
    weight: {
        regular: 400,
        medium: 500,
        semibold: 600,
        bold: 700,
    },

    /**
     * The pixel equivalent at the default root size is noted per line, since
     * that is the number anyone reasons in.
     *
     * The whole scale sits a step higher than a first draft of it, and the
     * reason is measured rather than aesthetic. Contrast requirements step down
     * at particular sizes: non-body text needs Lc 90 below 15px and only Lc 75
     * at or above it, and body text needs Lc 90 below 18px and Lc 75 at or
     * above. Under the old scale — 11, 13, 14, 16 — every style fell on the
     * strict side of both thresholds, and Lc 90 on a near-black page admits
     * only colours above about 91% lightness. Three levels of grey text were
     * therefore impossible: they existed, but no two of them were
     * distinguishable.
     *
     * Starting at 15 rather than 11 buys back the whole muted range. The cost
     * is a slightly less dense interface; the alternative was an interface with
     * one usable text colour.
     */
    size: {
        1: "0.9375rem", // 15
        2: "1rem",      // 16
        3: "1.0625rem", // 17
        4: "1.125rem",  // 18
        5: "1.25rem",   // 20
        6: "1.5rem",    // 24
        7: "1.75rem",   // 28
        8: "2.25rem",   // 36
    },

    line: {
        1: "1.25rem",   // 20
        2: "1.375rem",  // 22
        3: "1.5rem",    // 24
        4: "1.625rem",  // 26
        5: "1.75rem",   // 28
        6: "2rem",      // 32
        7: "2.25rem",   // 36
        8: "2.75rem",   // 44
    },

    /**
     * A monotonic scale from tightest to most open. Large type is set tighter
     * and small type needs the air, so a style's tracking follows from its size
     * — which is why these are steps rather than role names.
     */
    tracking: {
        tightest: "-0.02em",
        tighter: "-0.015em",
        tight: "-0.01em",
        snug: "-0.005em",
        normal: "0",
        wide: "0.06em",
    },
});
