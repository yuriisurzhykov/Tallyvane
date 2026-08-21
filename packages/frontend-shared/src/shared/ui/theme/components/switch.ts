import { defineComponentTokens } from "design-token-engine";

/**
 * Switch track/thumb/travel are this component's own geometry, not borrowed
 * spacing roles. Kept in one namespace so the four numbers that have to
 * add up (track minus insets minus thumb equals travel) live together, and
 * so DS201 does not fire when they happen to share a primitive with an
 * unrelated component.
 */
export const switchTokens = defineComponentTokens("switch", {
    trackWidth: "{dimension.10}",
    trackHeight: "{dimension.6}",
    thumbSize: "{dimension.4}",
    thumbTravel: "{dimension.4}",
});
