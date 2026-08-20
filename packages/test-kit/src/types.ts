/**
 * The shape every consumer's own list already has — `frontend-web`'s
 * `pages.manifest.ts`, and `packages/storybook`'s generated `story-manifest.ts`.
 * `path` is a full URL path (or, for Storybook, an `iframe.html?id=...` path):
 * whatever `page.goto` can take directly.
 */
export interface PageEntry {
    readonly name: string;
    readonly path: string;
    /**
     * Set when the page/story is structurally incapable of showing text — a
     * decorative primitive like `Separator` or a bare control story with no
     * accompanying label (`Checkbox`, `IconButton`). `defineApcaContrastSpecs`'s
     * own "at least one sample was measured" assertion exists precisely to
     * catch a walker that silently found nothing on a page that *should* have
     * text; a page that structurally never has any is a different, known case,
     * not the failure that assertion is watching for. Always `false`/absent
     * for `frontend-web`'s own manifest today — every real page has some text.
     */
    readonly skipTextCheck?: boolean;
}
