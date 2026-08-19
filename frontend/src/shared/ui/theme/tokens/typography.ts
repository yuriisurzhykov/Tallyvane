import { definePrimitives } from "design-token-engine";

/**
 * Type primitives.
 *
 * Sizes are named by role rather than by step, one name per entry in
 * `composites/text-styles.ts`. A generic `1..8` scale would be a regression
 * here: it forces every reader to hold a mapping in their head, and it lets a
 * heading quietly borrow the caption's size because 2 was next to 3. Named
 * roles also mean a size can move without every other size shifting under it.
 *
 * Line heights are unitless ratios, not fixed lengths — a ratio scales with
 * whatever size it is applied to, so one `normal` serves body and caption
 * alike, and a size change cannot leave its leading behind.
 *
 * Sizes are fixed rather than fluid. `clamp()` earns its complexity on a
 * marketing page that spans phone to billboard; this is a console with a
 * bounded content width, where a heading that resizes with the viewport just
 * makes two windows side by side disagree.
 *
 * Specification and the reasoning behind every choice:
 * docs/frontend/01-shared-design-tokens.md
 */
export const typography = definePrimitives({
    /**
     * `var(--font-ibm-plex-sans)`, not a quoted family name. Those custom
     * properties are set on `<html>` by `next/font/google` (see
     * `app/fonts.ts`), and their value already contains Next's own generated
     * fallback chain — a metric-matched face first, then the system default —
     * so the real font and its stand-in stay the same size and swapping between
     * them shifts nothing.
     *
     * Naming the families as quoted strings instead is the failure worth
     * guarding against: nothing loads them, every page renders in the system
     * fallback, and no error is raised anywhere. The stack after the variable
     * is the last resort for when the layout has not applied `.variable` at
     * all.
     */
    family: {
        sans: "var(--font-ibm-plex-sans), system-ui, sans-serif",
        mono: "var(--font-ibm-plex-mono), ui-monospace, SFMono-Regular, monospace",
    },

    /**
     * Stops at 700 because that is where IBM Plex Sans stops. Beyond it the
     * browser fabricates the weight by thickening the outlines, which reads as
     * a slightly blurred version of bold rather than as a heavier face.
     */
    weight: {
        regular: 400,
        medium: 500,
        semibold: 600,
        bold: 700,
    },

    /** The pixel equivalent at the default root size is noted per line, since that is the number anyone reasons in. */
    size: {
        pageTitle: "2rem",       // 32
        sectionTitle: "1.25rem", // 20
        cardTitle: "1.0625rem",  // 17
        body: "1rem",            // 16
        bodySmall: "0.875rem",   // 14
        label: "0.8125rem",      // 13
        caption: "0.6875rem",    // 11
        /**
         * Its own entry despite matching `bodySmall` today. Monospaced faces
         * carry a smaller x-height at the same nominal size, so this is the one
         * that will need nudging to sit level with the text around it — and it
         * can only be nudged if it is not shared.
         */
        numeric: "0.875rem",     // 14
    },

    lineHeight: {
        tight: "1.15",
        snug: "1.35",
        normal: "1.5",
        relaxed: "1.65",
    },

    tracking: {
        tight: "-0.02em",
        snug: "-0.015em",
        normal: "0",
        wide: "0.06em",
    },
});
