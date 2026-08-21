import { defineComponentTokens } from "design-token-engine";

/**
 * Scrollbar thickness is this component's geometry, not a spacing role at
 * the call site. The value is the already-required `{semantic.spacing.inline}`
 * step (0.5rem) rather than `{dimension.2}`: `statusBadge` already consumes
 * that primitive directly, and DS201 forbids a second component from doing
 * the same. Routing through the required role is the promotion that rule
 * asks for; the CSS still reads `--ds-component-scroll-area-thickness`.
 */
export const scrollAreaTokens = defineComponentTokens("scrollArea", {
    thickness: "{semantic.spacing.inline}",
});
