import { defineComponentTokens } from "design-token-engine";

/**
 * Drawer width is a structural length belonging to this one panel, not a
 * page-width role. A global `layout` role with a single consumer would
 * trip DS102; a component token pointing at the primitive is the shape
 * that rule asks for.
 */
export const drawerTokens = defineComponentTokens("drawer", {
    width: "{layout.448}",
});
