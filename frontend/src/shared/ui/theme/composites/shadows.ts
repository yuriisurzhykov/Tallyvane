import { defineComposite } from "design-token-engine";

/**
 * Two shadows, and the shortness of that list is the point.
 *
 * Elevation in this interface comes from value: the page sits a step below the
 * cards on it, so a card reads as raised with no shadow at all. A shadow is
 * spent only where value cannot do the job — on something that floats over
 * arbitrary content it did not lay out, and on the focus ring, which has to be
 * visible against every surface at once.
 *
 * Every colour here is a reference. A shadow tinted with its own hardcoded RGB
 * triple is a second, silent copy of a palette value that will not move when
 * the palette does; the validator rejects it outright.
 */
export const shadows = defineComposite("shadow", {
    /**
     * Menus, popovers and the floating action button. Tinted with the darkest
     * neutral rather than pure black, so the shadow carries the same faint
     * warmth as everything else instead of reading as a grey smudge.
     *
     * Two layers: a tight one for the contact edge and a wide, weak one for the
     * ambient falloff. A single layer has to choose between looking detached
     * and looking dirty.
     */
    overlayPanel: [
        {
            x: 0, y: 1,
            blur: 2,
            spread: 0,
            color: "alpha({color.neutral.1000}, 8%)",
        },
        {
            x: 0, y: 8,
            blur: 24,
            spread: -4,
            color: "alpha({color.neutral.1000}, 12%)",
        },
    ],

    /**
     * Drawn as a shadow rather than a border so it costs no layout and cannot
     * shift the element it marks. Goes through the `borderFocus` role, which is
     * near-black in light and near-white in dark — the ring inverts with the
     * theme without this file knowing anything about themes.
     */
    focusRing: [{
        x: 0, y: 0,
        blur: 0,
        spread: 2,
        color: "{theme.color.borderFocus}",
    }],
});
