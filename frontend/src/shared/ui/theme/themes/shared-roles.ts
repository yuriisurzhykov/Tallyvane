/**
 * The colour roles that are identical in both themes, factored out once and
 * merged into each theme rather than written twice.
 *
 * What qualifies is narrow, and the boundary is worth understanding. A status
 * colour is a fixed signal: green means an offer whatever the page behind it is
 * doing, and its wash is the same colour at the same opacity, so both survive a
 * theme change untouched. Anything whose job is to contrast with the page does
 * not qualify.
 *
 * That excludes several roles §4 of the specification lists here, and the
 * reason is the monochrome accent decided in ADR-029. An accent made of
 * neutrals necessarily inverts: near-white on a dark page, near-black on a light
 * one. `interactivePrimary`, its hover and pressed states, `interactivePrimaryText`,
 * `borderFocus` and `textOnAccent` therefore cannot be shared — sharing them
 * would put a white focus ring on a white page. They live in each theme
 * instead. The specification was written dark-first and says as much about its
 * light values; this is that correction.
 *
 * `textOnSolid` does qualify, because what it sits on is a status fill, which
 * is itself the same in both themes.
 */
export const sharedColorRoles = {
    statusSuccess: "{color.green.500}",
    statusSuccessSubtle: "alpha({color.green.500}, 14%)",
    statusDanger: "{color.red.500}",
    statusDangerSubtle: "alpha({color.red.500}, 14%)",
    statusAttention: "{color.amber.500}",
    statusAttentionSubtle: "alpha({color.amber.500}, 14%)",
    statusInfo: "{color.blue.500}",
    statusInfoSubtle: "alpha({color.blue.500}, 14%)",

    /**
     * Text printed on top of a solid status fill. Dark, not white: the 500 step
     * of every status scale is a mid-to-light tone, so white on it fails
     * contrast in all four cases.
     */
    textOnSolid: "{color.neutral.1000}",
} as const;
