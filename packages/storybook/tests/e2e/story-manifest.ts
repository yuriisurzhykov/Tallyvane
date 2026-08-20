import * as fs from "node:fs";
import * as path from "node:path";
import { fileURLToPath } from "node:url";
import type { PageEntry } from "test-kit/types";

/**
 * Storybook's own list of what exists, turned into the shape `test-kit`'s
 * spec functions already take. Nothing here is hand-maintained: a new
 * component's `.stories.tsx` shows up the next time `build-storybook` runs,
 * because `index.json` is generated from the file system, not written by
 * anyone.
 *
 * Read at test-run time rather than at config-load time deliberately —
 * `playwright.config.ts`'s `webServer` builds Storybook first, and this file
 * is only imported from inside a `.spec.ts`, which runs after that build has
 * already produced `storybook-static/index.json`.
 */
interface StorybookIndexEntry {
    readonly id: string;
    readonly type?: string;
    readonly tags?: readonly string[];
}

interface StorybookIndex {
    readonly entries: Readonly<Record<string, StorybookIndexEntry>>;
}

const STATIC_DIR = path.resolve(fileURLToPath(new URL(".", import.meta.url)), "..", "..", "storybook-static");

/**
 * A story tagged with this (`tags: [NO_VISIBLE_TEXT_TAG]` on the CSF3 export)
 * is exempt from `defineApcaContrastSpecs`'s "at least one sample was
 * measured" assertion — see `PageEntry.skipTextCheck`'s own comment for what
 * that assertion is actually for and why a structurally textless story is a
 * different case from it silently finding nothing on a page that should have
 * text. A tag, not a `parameters` flag: `storybook build`'s `index.json` is
 * generated statically from source without executing story modules, so
 * `tags` (which the index already carries) is the only per-story metadata
 * this file can read without loading each story's real module.
 */
export const NO_VISIBLE_TEXT_TAG = "no-visible-text";

export function readStoryManifest(): readonly PageEntry[] {
    const indexPath = path.join(STATIC_DIR, "index.json");
    const raw = fs.readFileSync(indexPath, "utf-8");
    const index = JSON.parse(raw) as StorybookIndex;

    return Object.values(index.entries)
        // Docs pages (`type: "docs"`) render the same components again inside
        // an autodocs page — checking them too would be double-counting, not
        // extra coverage.
        .filter((entry) => (entry.type ?? "story") === "story")
        .map((entry) => ({
            name: entry.id,
            path: `/iframe.html?id=${ entry.id }&viewMode=story`,
            skipTextCheck: (entry.tags ?? []).includes(NO_VISIBLE_TEXT_TAG),
        }));
}
