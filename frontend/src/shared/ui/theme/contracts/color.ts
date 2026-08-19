import { defineContract } from "design-token-engine";

/**
 * The roles every theme is obliged to define.
 *
 * "Required" is a structural claim, not a wish list: it means the interface
 * cannot be built without this role, so a theme that omits it is broken rather
 * than merely incomplete. `defineTheme()` enforces that the moment
 * `themes/light.ts` or `themes/dark.ts` is imported.
 *
 * It also has a second, less obvious job. Roles listed here are exempt from the
 * unused-role check, because their only consumer is `adapters/tailwind.css` —
 * a stylesheet, invisible to a graph that only sees TypeScript. Anything NOT
 * listed here must earn its place by being referenced from a component token or
 * a composite, or it gets reported. So the cost of over-listing is silence
 * exactly where the warning would be useful, which is why this list is short
 * and grows only on demand.
 *
 * Optional roles are not written here at all — a theme may simply declare them,
 * and parity between themes is checked separately.
 */
export const colorContract = defineContract({
    category: "color",
    required: [
        "surfacePage",
        "surfaceCard",
        "surfaceSunken",
        "surfaceHover",
        "surfaceOverlay",

        "textPrimary",
        "textSecondary",
        "textMuted",
        "textOnAccent",

        "borderSubtle",
        "borderDefault",
        "borderStrong",
        "borderFocus",

        "accent",
        "accentHover",
        "accentSubtle",

        "statusAttention",
        "statusAttentionSubtle",
        "statusSuccess",
        "statusSuccessSubtle",
        "statusDanger",
        "statusDangerSubtle",
        "statusInfo",
        "statusInfoSubtle",
    ],
});
