import { defineComponentTokens } from "design-token-engine";

/**
 * The vertical hairline joining events in an application's history.
 *
 * Its width is its own token rather than a spacing step: a connector is a drawn
 * line, and a line's thickness has nothing to do with the scale governing gaps
 * between things. Filing it under spacing would be a category error the next
 * person would reasonably act on.
 *
 * Both values go through roles rather than primitives, because a connector is
 * exactly what those roles describe — a border that happens to run vertically.
 * It should shift with every other border the day either is retuned, and
 * neither its colour nor its weight is special enough to deserve its own.
 */
export const timelineConnectorTokens = defineComponentTokens("timelineConnector", {
    color: "{theme.color.borderDefault}",
    width: "{semantic.border.default}",
});
