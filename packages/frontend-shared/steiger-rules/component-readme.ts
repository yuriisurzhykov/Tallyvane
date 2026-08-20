import { basename } from "node:path";
import { getLayers, getSegments } from "@feature-sliced/filesystem";
import type { File, Folder, PartialDiagnostic, Rule } from "@steiger/toolkit";

const README_FILE_NAME = "README.md";

/**
 * `File`/`Folder` from `@feature-sliced/filesystem` expose only `path` and
 * `type` — no `name` — so the filename has to be pulled out of `path`
 * ourselves. Matching with a hardcoded `/` separator broke on Windows,
 * where `path` uses `\`: verified empirically (a manual tree walk found
 * `theme/README.md` correctly; the real Steiger CLI run still reported
 * `theme` as missing one) before reaching for `path.basename`, which is
 * separator-agnostic by construction rather than a second hardcoded guess.
 */
function hasReadme(folder: Folder): boolean {
    return folder.children.some((child: File | Folder) => child.type === "file" && basename(child.path) === README_FILE_NAME);
}

/**
 * Every component directory under `shared/ui/` needs a live, colocated
 * `README.md` — `development-methodology.mdc` §3 (sharpened 2026-08-19) and
 * `component-authoring/SKILL.md` §7. Modeled directly on
 * `@feature-sliced/steiger-plugin`'s own `fsd/public-api` rule: same
 * `getLayers` → `getSegments` → walk `ui`'s immediate children traversal,
 * checking for a different filename.
 *
 * Scoped to `shared/ui/` only, not every segment (`api`, `lib`, `i18n`,
 * `config`, `blocks`) — those are still `export {}` stubs with no real
 * content yet, and the same two rules `steiger.config.ts` already disables
 * for that reason (`fsd/insignificant-slice`, `fsd/no-public-api-sidestep`)
 * apply to the same underlying fact.
 */
const componentReadme = {
    name: "local/component-readme" as const,
    check(root: Folder) {
        const diagnostics: PartialDiagnostic[] = [];
        const layers = getLayers(root);
        const shared = layers.shared;
        if (!shared) return { diagnostics };

        const segments = getSegments(shared);
        const ui = segments.ui;
        if (!ui || ui.type !== "folder") return { diagnostics };

        for (const child of ui.children) {
            if (child.type !== "folder") continue;
            if (!hasReadme(child)) {
                diagnostics.push({
                    message: `${child.path} is missing a README.md — every shared/ui component needs a live, colocated README (development-methodology.mdc §3).`,
                    location: { path: child.path },
                });
            }
        }

        return { diagnostics };
    },
} satisfies Rule;

export default componentReadme;
