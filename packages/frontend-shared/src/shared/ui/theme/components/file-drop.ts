import { defineComponentTokens } from "design-token-engine";

/**
 * The filename cap is this component's geometry. The drop-zone glyph is
 * `calc(var(--control-icon) * 1.5)` at the call site (one and a half of
 * the shared in-control icon), not a second primitive — pointing a
 * component token at `dimension.6` would collide with `switch.trackHeight`
 * under DS201, and the two are coincidence, not shared meaning.
 */
export const fileDropTokens = defineComponentTokens("fileDrop", {
    filenameMaxWidth: "{layout.256}",
});
