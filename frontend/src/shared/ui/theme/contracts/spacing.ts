import { defineContract } from "design-token-engine";

/**
 * Spacing roles are named by the job they do, not by size, because a number
 * cannot tell those jobs apart: `p-4` and `gap-4` can be the same value for
 * entirely unrelated reasons and then have to be changed separately.
 *
 * All required, because the sole consumer is the Tailwind adapter and a role no
 * stylesheet can find is a role that does not exist.
 */
export const spacingContract = defineContract({
    category: "spacing",
    required: [
        "inlineTight",
        "inline",
        "stackTight",
        "stack",
        "groupGap",
        "sectionGap",
        "screenPadding",
    ],
});
