import { definePrimitives } from "design-token-engine";

/**
 * The stacking ladder: rungs, not layers. Which rung a thing stands on is
 * decided in `semantic/z-index.ts`.
 *
 * Steps of ten, so a layer can be slipped between two others without
 * renumbering everything beneath it. The ladder deliberately runs no higher
 * than it needs to — a scale that stops at 80 makes `z-[9999]` at a call site
 * obviously foreign, whereas one that already reaches into the thousands
 * invites the next number up.
 */
export const zIndex = definePrimitives({
    0: 0,
    10: 10,
    20: 20,
    30: 30,
    40: 40,
    50: 50,
    60: 60,
    70: 70,
    80: 80,
});
