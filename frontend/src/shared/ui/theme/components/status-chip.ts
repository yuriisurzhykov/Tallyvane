import { defineComponentTokens } from "design-token-engine";

/**
 * The pill carrying an application's current status.
 *
 * A component token is the right home for a value that belongs to exactly one
 * component and would be misleading as a global name. The chip's padding is
 * that: it is tighter than anything else in the interface, because a pill has
 * to read as a label rather than a button, and promoting "unusually tight
 * padding" to the shared spacing scale would invite it into places where it is
 * simply wrong.
 *
 * The status colours are deliberately absent. Which of the four applies is a
 * decision about the application's state, made by whatever renders the chip;
 * baking a choice in here would mean four near-identical token sets and a
 * component that still has to pick between them.
 */
export const statusChipTokens = defineComponentTokens("statusChip", {
    radius: "{semantic.radius.pill}",
    paddingInline: "{semantic.spacing.insetSm}",
    paddingBlock: "{semantic.spacing.insetXs}",
});
