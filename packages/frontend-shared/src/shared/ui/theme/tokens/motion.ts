import { definePrimitives } from "design-token-engine";

/**
 * Durations and easing curves. Every animation built on these must also carry
 * a branch for `prefers-reduced-motion` — that is checked, not trusted.
 */
export const motion = definePrimitives({
    duration: {
        fast: "180ms",
        base: "240ms",
        slow: "360ms",
    },
    easing: {
        standard: "cubic-bezier(0.2, 0, 0, 1)",
        out: "cubic-bezier(0, 0, 0, 1)",
        in: "cubic-bezier(0.4, 0, 1, 1)",
    },
    scale: {
        press: 0.97,
        highlight: 1.02,
    },
});
