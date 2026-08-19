import { defineContract } from "design-token-engine";

/**
 * Four roles, covering the four things this interface actually rounds. The
 * primitive scale has five steps; the fifth exists so a role can be retuned
 * without inventing a value, not so a component can reach past this layer.
 */
export const radiusContract = defineContract({
    category: "radius",
    required: ["control", "card", "surface", "pill"],
});
