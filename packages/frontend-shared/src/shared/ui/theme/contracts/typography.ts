import { defineContract } from "design-token-engine";

/**
 * Only the faces. Sizes, weights and leadings reach their consumers through
 * `composites/text-styles.ts`, which welds them into whole styles, so naming
 * them individually here would hand everyone a way to set a size without the
 * leading that belongs to it.
 *
 * Two roles, because the interface has exactly two jobs for a typeface: reading
 * words, and reading figures that must line up in a column.
 */
export const typographyContract = defineContract({
    category: "typography",
    required: ["familyUi", "familyNumeric"],
});
