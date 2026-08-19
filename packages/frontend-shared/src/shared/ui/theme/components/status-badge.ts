import { defineComponentTokens } from "design-token-engine";

/**
 * The badge carrying an application's current status.
 *
 * A component token is the right home for a value belonging to exactly one
 * component that would mislead as a global name. The badge's padding is that:
 * it is tighter than anything else in the interface, because a badge has to
 * read as a label rather than a button, and promoting "unusually tight padding"
 * to the shared spacing scale would invite it into places where it is wrong.
 *
 * The status colours are deliberately absent. Which of the four applies is a
 * decision about the application's state, made by whatever renders the badge;
 * fixing one here would mean four near-identical token sets and a component
 * that still has to choose between them.
 */
export const statusBadgeTokens = defineComponentTokens("statusBadge", {
    /**
     * Primitives rather than spacing roles, and deliberately so. Density (§9.4)
     * moves row heights, card padding, table type and control heights — not
     * this. A badge's tightness is intrinsic to it being a label, so routing
     * these through the shared scale would subscribe it to changes it does not
     * want.
     */
    paddingX: "{dimension.2}",
    paddingY: "{dimension.1}",

    /**
     * A capsule, not the `chip` step §6 specifies. Six pixels of radius on a
     * badge roughly twenty-four tall reads as a rounded rectangle, which looks
     * like a small button — and a status is not something you press. Fully
     * round ends say "label" without any other cue.
     */
    radius: "{semantic.radius.pill}",

    dotSize: "{dimension.2}",
});
