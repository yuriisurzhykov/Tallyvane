import { defineContract } from "design-token-engine";

/**
 * All required, because the Tailwind adapter is the only consumer and a
 * structural role no stylesheet can reach does not exist.
 */
export const layoutContract = defineContract({
    category: "layout",
    required: ["consoleMaxWidth", "briefMaxWidth", "sidebarExpanded", "sidebarCollapsed"],
});
