import { defineComponentTokens } from "design-token-engine";

/**
 * The vertical hairline joining events in an application's history.
 *
 * Its width is its own token rather than a spacing step: a connector is a
 * drawn line, and a line's thickness has nothing to do with the scale that
 * governs gaps between things. Filing it under spacing would be a category
 * error that the next person would reasonably act on.
 *
 * The colour goes through `borderDefault` because a connector is exactly that —
 * a border that happens to run vertically — and it should shift with every
 * other border the day that role is retuned.
 */
export const timelineConnectorTokens = defineComponentTokens("timelineConnector", {
    color: "{theme.color.borderDefault}",
    width: "{border.hairline}",
});
