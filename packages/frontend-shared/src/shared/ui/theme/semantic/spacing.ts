import { defineTheme } from "design-token-engine";
import { spacingContract } from "../contracts/spacing";

/**
 * Named by job, mapped onto the primitive scale. `groupGap` and `screenPadding`
 * resolve to the same step today, and that coincidence is the argument for the
 * layer rather than against it: they mean different things, so they are free to
 * diverge without anyone having to work out which of the two a given `p-6`
 * meant.
 *
 * This is also the layer density acts on. Changing what `stack` resolves to
 * retunes the whole interface at once; with components spelling out `p-4`
 * instead, a density switch would have to rewrite every call site, which is the
 * same as saying it could not exist.
 */
export const spacingRole = defineTheme(spacingContract, {
    /** Between an icon and its label. */
    inlineTight: "{dimension.1}",
    inline: "{dimension.2}",

    stackTight: "{dimension.3}",
    /** Between elements inside a card. */
    stack: "{dimension.4}",

    /** Between cards. */
    groupGap: "{dimension.6}",
    sectionGap: "{dimension.8}",
    screenPadding: "{dimension.6}",
});
