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

    /**
     * Three levels within a narrow band, and the narrowness is measured rather
     * than chosen: on the darkest surface a role has to reach Lc 75 to be
     * legible at the sizes this interface sets, which admits nothing below
     * about 82% lightness. These sit at 94, 88 and 83 — 95.0, 85.0 and 77.5 —
     * distinguishable side by side and each clearing its bar.
     *
     * Not `neutral.0` for the top: pure white on a near-black ground haloes,
     * and small text appears to vibrate.
     */
    textPrimary: "{color.neutral.100}",
    textSecondary: "{color.neutral.200}",
    textMuted: "{color.neutral.300}",
    /** Held only to the spot-readable level, because looking unavailable is the entire job. */
    textDisabled: "{color.neutral.500}",
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

    /**
     * Status as TEXT on the page, which is a different job from the fill and
     * takes the opposite end of each ramp.
     *
     * Two steps lighter than a first attempt, because HSL lightness is not
     * luminance: red and blue at 70-76% lightness measured Lc 57 and Lc 60
     * against the page, well short, while green at the same nominal lightness
     * reached 78. Every one of these was chosen from the measurement rather
     * than from the step number looking right.
     */
    statusSuccessText: "{color.green.200}",
    /** A step lighter than its neighbours: red is the least luminous hue of the four, and at the same nominal lightness it measured Lc 72 where the others cleared 75. */
    statusDangerText: "{color.red.100}",
    statusAttentionText: "{color.amber.200}",
    statusInfoText: "{color.blue.200}",
}));
