import { defineContract } from "design-token-engine";

/**
 * Five roles, one per kind of thing this interface rounds.
 *
 * `surface` is where this departs from the specification, which calls the role
 * `panel`. Same meaning, and the name was chosen deliberately.
 */
export const radiusContract = defineContract({
    category: "radius",
    required: ["chip", "control", "card", "surface", "pill"],
});
