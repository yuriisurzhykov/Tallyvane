import { defineContract } from "design-token-engine";

/**
 * Spacing roles are named by the job they do, not by size, and there are three
 * jobs: padding inside a component, the gap between stacked elements, and the
 * gap between regions of a page. A number cannot tell those apart, which is why
 * `p-4` and `gap-4` can be the same value for entirely unrelated reasons and
 * then have to be changed separately.
 *
 * All of them are required, because the sole consumer is the Tailwind adapter
 * and a role no stylesheet can find is a role that does not exist.
 */
export const spacingContract = defineContract({
    category: "spacing",
    required: [
        "insetXs",
        "insetSm",
        "insetMd",
        "insetLg",

        "stackXs",
        "stackSm",
        "stackMd",

        "sectionSm",
        "sectionMd",
    ],
});
