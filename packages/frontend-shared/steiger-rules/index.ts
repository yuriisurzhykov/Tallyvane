import { createConfigs, createPlugin, enableAllRules } from "@steiger/toolkit";
import componentReadme from "./component-readme.ts";

/**
 * This package's own local Steiger rules — structural checks specific to
 * this codebase's conventions, not general FSD methodology (that's
 * `@feature-sliced/steiger-plugin`'s job). One rule so far; add more
 * `ruleDefinitions` entries here rather than a second plugin file.
 */
const plugin = createPlugin({
    meta: { name: "tallyvane-local", version: "0.0.0" },
    ruleDefinitions: [componentReadme],
});

const configs = createConfigs({
    recommended: enableAllRules(plugin),
});

export default { plugin, configs };
