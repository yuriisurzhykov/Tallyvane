import { defineTheme } from "design-token-engine";
import { colorContract } from "../contracts/color";

/**
 * The same role names as `light.ts`, travelling the same scales in the opposite
 * direction. Read the two side by side: any role whose line here is not the
 * mirror of its line there is either a deliberate contrast correction or a
 * mistake, and the diff is the only place that distinction is visible.
 */
export const darkTheme = defineTheme(colorContract, {
    // Same inversion as light: the page is darker than the cards on it, so a
    // card reads as raised through value alone.
    surfacePage: "{color.neutral.1000}",
    surfaceCard: "{color.neutral.950}",
    surfaceSunken: "{color.overlayWhite.4}",
    surfaceHover: "{color.overlayWhite.4}",
    surfaceOverlay: "{color.scrim.dark}",

    // Not `neutral.0` — pure white on a near-black ground produces halation,
    // and the text appears to vibrate at small sizes. One step in costs nothing
    // legible and removes the effect.
    textPrimary: "{color.neutral.50}",
    textSecondary: "{color.neutral.300}",
    textMuted: "{color.neutral.400}",
    textOnAccent: "{color.neutral.1000}",

    borderSubtle: "{color.overlayWhite.8}",
    borderDefault: "{color.overlayWhite.12}",
    borderStrong: "{color.overlayWhite.24}",
    borderFocus: "{color.neutral.50}",

    accent: "{color.neutral.50}",
    accentHover: "{color.neutral.0}",
    accentSubtle: "{color.overlayWhite.8}",

    // Light steps for the foreground, mirroring light theme's dark ones. The
    // washes sit slightly stronger than in light because a translucent colour
    // loses more presence over a dark ground than over a pale one.
    statusAttention: "{color.amber.300}",
    statusAttentionSubtle: "alpha({color.amber.500}, 18%)",
    statusSuccess: "{color.green.300}",
    statusSuccessSubtle: "alpha({color.green.500}, 18%)",
    statusDanger: "{color.red.300}",
    statusDangerSubtle: "alpha({color.red.500}, 16%)",
    statusInfo: "{color.blue.300}",
    statusInfoSubtle: "alpha({color.blue.500}, 16%)",
});
