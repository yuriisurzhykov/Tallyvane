import { defineComposite } from "design-token-engine";

/**
 * A duration welded to the easing that belongs with it.
 *
 * Kept apart, these two get recombined by hand at every call site, and an
 * interface drifts into hovering one way here and another way three files over
 * without anyone having chosen either. Four names cover every motion this
 * product has, and a fifth should be argued for rather than added.
 *
 * Asymmetry between `enter` and `exit` is deliberate and is the one rule worth
 * knowing: things arrive slowly enough to be noticed and leave quickly, because
 * a leaving element is already irrelevant and waiting for it reads as lag.
 *
 * Whatever uses these still needs its own `prefers-reduced-motion` branch. A
 * duration token cannot express "and none of this when the reader has asked for
 * stillness".
 */
export const transitions = defineComposite("transition", {
    hover: {
        duration: "{motion.duration.fast}",
        easing: "{motion.easing.standard}",
    },
    press: {
        duration: "{motion.duration.fast}",
        easing: "{motion.easing.in}",
    },
    enter: {
        duration: "{motion.duration.base}",
        easing: "{motion.easing.out}",
    },
    exit: {
        duration: "{motion.duration.fast}",
        easing: "{motion.easing.in}",
    },
});
