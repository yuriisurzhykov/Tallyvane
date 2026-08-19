import { defineContract } from "design-token-engine";

/**
 * The missing control-height scale. `Button`, `IconButton`, `Input` and every
 * other interactive control need a height, and nothing in this tree named one
 * before now — sizes existed only as a mention in `COMPONENTS.md`, with no
 * token backing it.
 */
export const controlContract = defineContract({
    category: "control",
    required: ["heightSm", "heightMd", "heightLg"],
});
