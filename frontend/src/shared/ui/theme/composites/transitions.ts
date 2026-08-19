import { defineComposite } from "design-token-engine";

/**
 * A duration welded to the easing that belongs with it.
 *
 * Kept apart, the two get recombined by hand at every call site, and an
 * interface drifts into hovering one way here and another way three files over
 * without anyone having chosen either. Three names cover every motion this
 * product has, and a fourth should be argued for rather than added.
 *
 * They are ordered by how far the thing being animated travels: a hover changes
 * colour in place and should be over almost before it registers, a popover
 * crosses a short distance, a drawer crosses the screen. A single duration
 * applied to all three makes the first feel sluggish and the last feel snapped.
 *
 * Whatever uses these still needs its own `prefers-reduced-motion` branch — a
 * duration token cannot express "and none of this when the reader has asked for
 * stillness".
 */
export const transitions = defineComposite("transition", {
    hover: {
        duration: "{motion.duration.fast}",
        easing: "{motion.easing.standard}",
    },
    popover: {
        duration: "{motion.duration.base}",
        easing: "{motion.easing.out}",
    },
    drawer: {
        duration: "{motion.duration.slow}",
        easing: "{motion.easing.standard}",
    },
});
