/**
 * The shape every consumer's own list already has — `frontend-web`'s
 * `pages.manifest.ts`, and `packages/storybook`'s generated `story-manifest.ts`.
 * `path` is a full URL path (or, for Storybook, an `iframe.html?id=...` path):
 * whatever `page.goto` can take directly.
 */
export interface PageEntry {
    readonly name: string;
    readonly path: string;
}
