import { defineComposite } from "design-token-engine";

/**
 * Three elevations, and the shortness of that list is the point.
 *
 * A shadow is spent only on what genuinely floats above content it did not lay
 * out — menus, popovers, dialogs. A card in the page flow is separated by its
 * border and its fill instead: on a dark ground a shadow barely reads at all,
 * and faking depth by raising brightness turns the screen into porridge of grey
 * rectangles within a few components.
 *
 * The focus ring is deliberately not here. It is a two-pixel outline, not a
 * shadow — see `semantic/border.ts` and the `focus-ring` utility in the
 * Tailwind adapter.
 *
 * Every colour is a reference. A shadow tinted with its own hardcoded value is
 * a second, silent copy of a palette entry that will not move when the palette
 * does; the validator rejects it outright.
 */
export const shadows = defineComposite("shadow", {
    elevation1: [{
        x: 0, y: 1,
        blur: 2,
        spread: 0,
        color: "alpha({color.neutral.1000}, 40%)",
    }],
    elevation2: [{
        x: 0, y: 4,
        blur: 12,
        spread: 0,
        color: "alpha({color.neutral.1000}, 40%)",
    }],
    elevation3: [{
        x: 0, y: 12,
        blur: 32,
        spread: 0,
        color: "alpha({color.neutral.1000}, 60%)",
    }],
});
