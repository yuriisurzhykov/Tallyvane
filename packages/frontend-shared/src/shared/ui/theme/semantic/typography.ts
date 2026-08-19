import { defineTheme } from "design-token-engine";
import { typographyContract } from "../contracts/typography";

/**
 * The typeface roles.
 *
 * The primitives below these are named `sans` and `mono`, which describe how
 * the letters look rather than what they are for — the same failing as calling
 * a colour `neutral-800` instead of `textPrimary`. Should the interface face
 * ever become a serif, `family.sans` turns into a lie while `familyUi` stays
 * exactly as true as it was.
 */
export const typographyRole = defineTheme(typographyContract, {
    familyUi: "{typography.family.sans}",

    /** Figures that have to align by digit: pay, dates, identifiers. `composites/text-styles.ts`'s `numeric` is the only consumer, which is why `app/fonts.ts` loads a single weight of the mono face. */
    familyNumeric: "{typography.family.mono}",
});
