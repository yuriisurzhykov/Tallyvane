import { defineContract } from "design-token-engine";

/**
 * The roles every theme is obliged to define, taken from
 * docs/frontend/01-shared-design-tokens.md §3.
 *
 * "Required" is a structural claim, not a wish list: it means the interface
 * cannot be built without this role, so a theme that omits it is broken rather
 * than merely incomplete. `defineTheme()` enforces that the moment
 * `themes/dark.ts` or `themes/light.ts` is imported.
 *
 * It also has a second, less obvious job. Roles listed here are exempt from the
 * unused-role check, because their only consumer is `adapters/tailwind.css` —
 * a stylesheet, invisible to a graph that only sees TypeScript. Anything NOT
 * listed here must earn its place by being referenced from a component token or
 * a composite, or it gets reported.
 *
 * Each status carries three roles rather than one: the colour itself, a subtle
 * wash to sit behind it, and a text colour that holds contrast against the page.
 * They are not interchangeable — a status colour bright enough to register as a
 * dot is rarely dark enough to read as a word.
 */
export const colorContract = defineContract({
    category: "color",
    required: [
        "surfacePrimary",
        "surfaceElevated",
        "surfaceInset",
        "surfaceRowHover",
        "surfaceSelected",
        "surfaceOverlay",

        "textPrimary",
        "textSecondary",
        "textMuted",
        "textDisabled",
        "textOnAccent",
        "textOnSolid",

        "borderSubtle",
        "borderDefault",
        "borderStrong",
        "borderFocus",

        "interactivePrimary",
        "interactivePrimaryHover",
        "interactivePrimaryPressed",
        "interactivePrimarySubtle",
        "interactivePrimaryText",

        "statusSuccess",
        "statusSuccessSubtle",
        "statusSuccessText",
        "statusDanger",
        "statusDangerSubtle",
        "statusDangerText",
        "statusAttention",
        "statusAttentionSubtle",
        "statusAttentionText",
        "statusInfo",
        "statusInfoSubtle",
        "statusInfoText",
    ],
});
