import { defineContract } from "design-token-engine";

/**
 * Every layer the interface has, and the list is closed on purpose: if a thing
 * overlaps another thing, its layer is named here. A component reaching for a
 * number instead is deciding an argument the rest of the interface was never
 * told about.
 */
export const zIndexContract = defineContract({
    category: "z",
    required: [
        "background",
        "content",
        "sidebar",
        "fab",
        "popover",
        "scrim",
        "modal",
        "toast",
        "tooltip",
    ],
});
