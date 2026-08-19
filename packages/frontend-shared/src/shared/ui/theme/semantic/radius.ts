import { defineTheme } from "design-token-engine";
import { radiusContract } from "../contracts/radius";

/**
 * Radius has no theme axis: a corner is the same corner in light and dark. It
 * lives here rather than under `themes/` for that reason alone.
 *
 * The roles climb with the size of the thing they round, which is what keeps a
 * button from looking like a panel. `surface` sits one step above `card` so
 * that a card inside a large container never appears rounder than the container
 * holding it — the effect that makes nesting look accidental.
 */
export const radiusRole = defineTheme(radiusContract, {
    chip: "{radius.sm}",
    control: "{radius.md}",
    card: "{radius.lg}",
    surface: "{radius.xl}",
    pill: "{radius.full}",
});
