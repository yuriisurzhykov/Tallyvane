import { defineComponentTokens } from "design-token-engine";

/**
 * How big a QR code is drawn. A code has no useful range of sizes (too small and a phone cannot find it, too big
 * and it does not fit a phone's screen), so the size is this component's own measure and not a page-width role:
 * a global `layout` role with one consumer would trip DS102.
 */
export const qrCodeTokens = defineComponentTokens("qrCode", {
    size: "{layout.288}",
});
