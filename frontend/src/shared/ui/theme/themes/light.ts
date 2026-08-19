import { defineTheme, mergeTokenTree } from "design-token-engine";
import { colorContract } from "../contracts/color";
import { sharedColorRoles } from "./shared-roles";

/**
 * The same roles as `dark.ts`, travelling the same scales in the opposite
 * direction. Read the two side by side: any line here that is not the mirror of
 * its counterpart is either a deliberate contrast correction or a mistake, and
 * the diff is the only place that distinction shows.
 *
 * Declared but not yet verified. A warm neutral scale can read as yellow on a
 * light ground in a way it never does on a dark one, which is the specific
 * thing to look for when this theme first meets a real screen.
 */
export const lightTheme = defineTheme(colorContract, mergeTokenTree(sharedColorRoles, {
    // The page sits one step down from the cards on it. Inverting the obvious
    // arrangement is what lets a card read as raised without a shadow, which is
    // the only way to get elevation while keeping the promise of no heavy
    // shadows.
    surfacePrimary: "{color.neutral.50}",
    surfaceElevated: "{color.neutral.0}",
    surfaceInset: "{color.neutral.100}",
    surfaceRowHover: "{color.overlayBlack.4}",
    surfaceSelected: "{color.overlayBlack.12}",
    surfaceOverlay: "{color.scrim.light}",

    textPrimary: "{color.neutral.900}",
    textSecondary: "{color.neutral.700}",
    textMuted: "{color.neutral.600}",
    textDisabled: "{color.neutral.400}",
    textOnAccent: "{color.neutral.0}",

    borderSubtle: "{color.overlayBlack.8}",
    borderDefault: "{color.overlayBlack.12}",
    borderStrong: "{color.overlayBlack.24}",
    borderFocus: "{color.neutral.900}",

    interactivePrimary: "{color.neutral.900}",
    // Darkens rather than lightens — the light theme's direction of travel.
    interactivePrimaryHover: "{color.neutral.1000}",
    interactivePrimaryPressed: "{color.neutral.800}",
    interactivePrimarySubtle: "{color.overlayBlack.12}",
    interactivePrimaryText: "{color.neutral.900}",

    // Dark steps, mirroring the light ones the dark theme uses. The 500s that
    // carry the status colour itself are far too light to read as text here.
    statusSuccessText: "{color.green.800}",
    statusDangerText: "{color.red.800}",
    statusAttentionText: "{color.amber.800}",
    statusInfoText: "{color.blue.600}",
}));
