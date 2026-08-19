import { defineTheme } from "design-token-engine";
import { colorContract } from "../contracts/color";

/**
 * The default theme. It is listed first in `compiler.config.ts`, which is what
 * makes it compile to `:root` while dark compiles to a `.theme-dark` override —
 * so an unstyled first paint lands on light rather than on nothing.
 *
 * There is no `shared-roles.ts` here, unlike other projects built on this
 * engine. Nothing to share: the accent is monochrome and therefore inverts
 * between the themes, and each status colour takes a different step in each
 * direction to hold contrast. Factoring out a common set would mean building
 * the de-duplication mechanism before any duplication exists. If roles do
 * converge later, that is the moment to extract them.
 *
 * The contrast ratios these steps produce are reasoned, not yet measured. Once
 * there is a screen to point an audit at, expect some of these to move — the
 * usual casualties are muted text and status colours on their own subtle
 * backgrounds.
 */
export const lightTheme = defineTheme(colorContract, {
    // The page sits one step DOWN from the cards on it. Inverting the obvious
    // arrangement is what lets a card read as raised without a shadow, which is
    // the only way to get elevation while keeping the promise of no heavy
    // shadows.
    surfacePage: "{color.neutral.50}",
    surfaceCard: "{color.neutral.0}",
    surfaceSunken: "{color.overlayBlack.4}",
    surfaceHover: "{color.overlayBlack.4}",
    surfaceOverlay: "{color.scrim.light}",

    textPrimary: "{color.neutral.900}",
    textSecondary: "{color.neutral.700}",
    textMuted: "{color.neutral.600}",
    textOnAccent: "{color.neutral.0}",

    borderSubtle: "{color.overlayBlack.8}",
    borderDefault: "{color.overlayBlack.12}",
    borderStrong: "{color.overlayBlack.24}",
    borderFocus: "{color.neutral.900}",

    // Monochrome, by decision: the accent is the darkest neutral rather than a
    // hue. A primary button is near-black here and near-white in dark. This is
    // what keeps amber free to mean "needs you" and nothing else.
    accent: "{color.neutral.900}",
    accentHover: "{color.neutral.1000}",
    accentSubtle: "{color.overlayBlack.8}",

    // Status colours take a dark step for the foreground and a wash of the mid
    // step for the background. The wash is built with `alpha()` over the 500
    // rather than by reaching for a 50/100 step, so the two always stay the
    // same hue — a hand-picked pale step drifts the moment the ramp is retuned.
    statusAttention: "{color.amber.800}",
    statusAttentionSubtle: "alpha({color.amber.500}, 14%)",
    statusSuccess: "{color.green.700}",
    statusSuccessSubtle: "alpha({color.green.500}, 14%)",
    statusDanger: "{color.red.700}",
    statusDangerSubtle: "alpha({color.red.500}, 12%)",
    statusInfo: "{color.blue.600}",
    statusInfoSubtle: "alpha({color.blue.500}, 12%)",
});
