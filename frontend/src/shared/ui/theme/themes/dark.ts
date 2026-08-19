import { defineTheme, mergeTokenTree } from "design-token-engine";
import { colorContract } from "../contracts/color";
import { sharedColorRoles } from "./shared-roles";

/**
 * The default theme. It is listed first in `compiler.config.ts`, which is what
 * makes it compile to `:root` while light compiles to a `.theme-light`
 * override — and it is the theme the first render deterministically produces,
 * on the server and the client alike (§12).
 *
 * Only the roles that differ between themes are written here; the rest arrive
 * through `mergeTokenTree` from `shared-roles.ts`.
 *
 * Contrast ratios are reasoned, not yet measured. Expect some of these to move
 * once there is a screen to point an audit at — the usual casualties are muted
 * text and status text on its own wash.
 */
export const darkTheme = defineTheme(colorContract, mergeTokenTree(sharedColorRoles, {
    surfacePrimary: "{color.neutral.1000}",
    surfaceElevated: "{color.neutral.950}",
    surfaceInset: "{color.neutral.900}",
    surfaceRowHover: "{color.overlayWhite.4}",
    /**
     * An overlay rather than a tint. The specification suggests a wash of amber
     * here, which predates the decision that amber means "this needs you" and
     * nothing else — a selected row is not asking for anything, so tinting it
     * with the attention colour would spend the one signal the interface has.
     */
    surfaceSelected: "{color.overlayWhite.12}",
    surfaceOverlay: "{color.scrim.dark}",

    /** Not `neutral.0`: pure white on a near-black ground haloes, and small text appears to vibrate. */
    textPrimary: "{color.neutral.100}",
    textSecondary: "{color.neutral.400}",
    textMuted: "{color.neutral.500}",
    textDisabled: "{color.neutral.600}",
    /** Text printed on the accent, which here is near-white — so this is near-black. Inverted in the light theme. */
    textOnAccent: "{color.neutral.1000}",

    borderSubtle: "{color.overlayWhite.8}",
    borderDefault: "{color.overlayWhite.12}",
    borderStrong: "{color.overlayWhite.24}",
    /**
     * Monochrome, like the accent, and for a reason beyond consistency. An
     * amber ring would be tempting, focus being a kind of attention — but the
     * screen would then carry two amber signals, "this record needs action" and
     * "your cursor is here". One is about the work and the other about
     * navigation, and they must not be confused.
     */
    borderFocus: "{color.neutral.0}",

    interactivePrimary: "{color.neutral.100}",
    interactivePrimaryHover: "{color.neutral.0}",
    interactivePrimaryPressed: "{color.neutral.200}",
    interactivePrimarySubtle: "{color.overlayWhite.12}",
    /** The accent used AS text — a link, a quiet button label — rather than as a fill behind text. */
    interactivePrimaryText: "{color.neutral.100}",

    statusSuccessText: "{color.green.300}",
    statusDangerText: "{color.red.300}",
    statusAttentionText: "{color.amber.300}",
    statusInfoText: "{color.blue.300}",
}));
