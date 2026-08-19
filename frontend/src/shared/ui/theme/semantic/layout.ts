import { defineTheme } from "design-token-engine";
import { layoutContract } from "../contracts/layout";

/**
 * What each structural length is for. These are the only layout names anything
 * outside this directory ever sees; the measurements they point at are not
 * exposed at all.
 */
export const layoutRole = defineTheme(layoutContract, {
    consoleMaxWidth: "{layout.1280}",

    /**
     * The brief is the one page allowed past the console width. It has to fit
     * on one screen of a 17-to-24-inch monitor, and three columns squeezed into
     * 1280 come out too narrow to read — the reason this exception exists,
     * rather than a general preference for wide layouts.
     */
    briefMaxWidth: "{layout.1600}",

    sidebarExpanded: "{layout.240}",
    sidebarCollapsed: "{layout.64}",
});
