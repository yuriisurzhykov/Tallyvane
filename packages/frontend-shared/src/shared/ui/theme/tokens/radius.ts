import { definePrimitives } from "design-token-engine";

/**
 * Corner radii. Deliberately larger than is usual for a developer tool — that
 * generosity is part of the warm character, and it is the one place the design
 * spends softness freely.
 *
 * There are no circles in this system apart from avatars and status dots, so
 * `full` is reserved for those.
 */
export const radius = definePrimitives({
    sm: "0.375rem",
    md: "0.625rem",
    lg: "0.875rem",
    xl: "1.25rem",
    // px, alone in this file: `full` is not a size but the idiom for "as round
    // as this box allows", and scaling it with the reader's font size would be
    // meaningless.
    full: "9999px",
});
