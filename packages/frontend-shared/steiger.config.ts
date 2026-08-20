import { defineConfig } from "steiger";
import type { Rule } from "@steiger/toolkit";
import fsd from "@feature-sliced/steiger-plugin";
import local from "./steiger-rules/index.ts";

/**
 * This package holds exactly one FSD layer — `shared` — because that is all
 * that ever belonged here: see ARCHITECTURE.md's amendment to §12.5. The same
 * two rules `frontend-web/steiger.config.ts` disables are disabled here for the
 * same reason: the segments are declared before they have real content
 * (`api`, `i18n`, `lib`, `config`, `blocks` are still `export {}` stubs).
 *
 * `**\/*.md` is scoped to the FSD plugin's own rule-config object rather
 * than a bare `{ ignores }` entry — verified empirically that a bare
 * top-level `ignores` is a *global* ignore, pruning matched paths from the
 * folder tree every rule receives (not just filtering FSD's diagnostics
 * afterward). That silently hid `theme/README.md` from `local/component-readme`
 * too, which needs to see every `.md` file to do its job.
 *
 * `defineConfig`'s `Rules` type parameter is given explicitly as the plain
 * `Rule[]` base type rather than left to inference: with two plugins' rule
 * sets combined in one array, TS otherwise locks `Rules` to whichever
 * plugin's literal rule-name union it infers first and rejects every entry
 * from the other. The trade-off is losing autocomplete on `rules: {...}`
 * keys in this file — an acceptable one for a config file with four rule
 * names in it total.
 */
const [fsdPlugin, fsdRuleConfig] = fsd.configs.recommended;
if (!fsdPlugin || !fsdRuleConfig) {
    throw new Error("@feature-sliced/steiger-plugin's `configs.recommended` shape changed — expected [plugin, ruleConfig].");
}

export default defineConfig<Rule[]>([
    fsdPlugin,
    {
        ...fsdRuleConfig,
        ignores: ["src/shared/ui/theme/generated/**", "**/*.md"],
    },
    ...local.configs.recommended,
    {
        files: ["./src/**"],
        rules: {
            "fsd/insignificant-slice": "off",
            "fsd/no-public-api-sidestep": "off",
        },
    },
]);
