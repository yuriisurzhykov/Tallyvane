import { defineContract } from "design-token-engine";

/**
 * Two roles, and the interface needs no third: every border is one pixel, and
 * two is reserved for the focus ring, where the extra weight is doing
 * accessibility work rather than decoration (§9.2).
 */
export const borderContract = defineContract({
    category: "border",
    required: ["default", "focus"],
});
