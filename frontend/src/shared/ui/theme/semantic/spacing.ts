import { defineTheme } from "design-token-engine";
import { spacingContract } from "../contracts/spacing";

/**
 * Named by job, mapped onto the primitive scale. Several roles deliberately
 * resolve to the same step right now — `insetMd` and `stackMd` are both one
 * rem — and that coincidence is the point of the layer rather than an argument
 * against it: they mean different things, so they are free to diverge without
 * anyone having to work out which of the two a given `p-4` meant.
 *
 * This is also the layer density will act on. Changing what `insetMd` resolves
 * to retunes the whole interface at once; with components spelling out `p-4`
 * instead, a density switch would have to rewrite every call site, which is
 * the same as saying it could not exist.
 */
export const spacingRole = defineTheme(spacingContract, {
    insetXs: "{dimension.1}",
    insetSm: "{dimension.2}",
    insetMd: "{dimension.4}",
    insetLg: "{dimension.6}",

    stackXs: "{dimension.2}",
    stackSm: "{dimension.3}",
    stackMd: "{dimension.4}",

    sectionSm: "{dimension.8}",
    sectionMd: "{dimension.12}",
});
