/**
 * The colour roles that are identical in both themes, factored out once and
 * merged into each rather than written twice.
 *
 * What qualifies is narrow. A status FILL is a fixed signal carrying its own
 * text, so it survives a theme change untouched; anything whose job is to
 * contrast with the page does not, which is why the accent, the focus ring and
 * the status TEXT colours all live per theme instead.
 *
 * The fills are deep rather than mid-toned, and that is the correction this
 * round made. Sitting at half lightness they were unusable in both directions
 * at once — too light for white text, too dark for black — which measured out
 * as Lc 34 and Lc 39 for the two text colours on the same fill. Dark fills with
 * white text clear the bar with room to spare and, unlike the alternative, keep
 * the colours saturated rather than washing them out to pastel.
 */
export const sharedColorRoles = {
    /**
     * The solid fill, and the dot drawn from it. Not the colour to set status
     * TEXT in — that is `statusSuccessText` and friends, which have to contrast
     * with the page instead and therefore differ per theme.
     */
    /**
     * The steps differ by hue on purpose. Equal lightness is not equal
     * contrast: green and yellow are the most luminous hues there are, so at
     * the depth where red and blue already carry white text comfortably, those
     * two were still at Lc 57 and Lc 64. They go a step deeper. Picking one
     * step number for all four would have looked tidy in the source and been
     * wrong on screen.
     */
    statusSuccess: "{color.green.800}",
    statusSuccessSubtle: "alpha({color.green.500}, 14%)",
    statusDanger: "{color.red.700}",
    statusDangerSubtle: "alpha({color.red.500}, 14%)",
    statusAttention: "{color.amber.800}",
    statusAttentionSubtle: "alpha({color.amber.500}, 14%)",
    statusInfo: "{color.blue.600}",
    statusInfoSubtle: "alpha({color.blue.500}, 14%)",

    /** Text printed on a solid status fill. White, now that the fills are deep enough to carry it. */
    textOnSolid: "{color.neutral.0}",
} as const;
