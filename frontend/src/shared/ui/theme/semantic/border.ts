import { defineTheme } from "design-token-engine";
import { borderContract } from "../contracts/border";

/**
 * Border widths by job. Two roles over two primitives looks like a formality
 * until the day the focus ring needs three pixels on high-density screens and
 * ordinary borders do not — at which point this is one line rather than a
 * search across every component that ever drew a ring.
 */
export const borderRole = defineTheme(borderContract, {
    default: "{border.hairline}",
    focus: "{border.focus}",
});
