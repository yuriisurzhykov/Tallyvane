import { defineTheme } from "design-token-engine";
import { zIndexContract } from "../contracts/z-index";

/**
 * Which rung of the ladder each layer stands on. Read top to bottom, this file
 * is the stacking order of the whole interface, which is the point of keeping
 * it in one place: the question "does this go above that" has an answer that
 * can be looked up instead of discovered by testing.
 */
export const zIndexRole = defineTheme(zIndexContract, {
    /** Decorative washes and patterns, which must stay behind everything real. */
    background: "{z.0}",
    content: "{z.10}",

    /** Persistent chrome. Content scrolls underneath it rather than over it. */
    sidebar: "{z.20}",

    /** The floating action button: above the page and its navigation, and deliberately below the scrim, so an open dialog covers it like everything else. */
    fab: "{z.30}",

    /** Menus, dropdowns, the action button's own expanded list — anything anchored to a trigger and dismissed by clicking away. */
    popover: "{z.40}",

    scrim: "{z.50}",
    modal: "{z.60}",

    /** Above the modal on purpose: a toast confirming what just happened inside a dialog is worthless if the dialog hides it. */
    toast: "{z.70}",

    /** Last, and nothing goes above it. A tooltip can be summoned from a control on any layer here, including a toast. */
    tooltip: "{z.80}",
});
