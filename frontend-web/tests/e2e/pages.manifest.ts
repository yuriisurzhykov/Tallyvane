/**
 * Every page the suites walk. The shape is a list so that adding a screen
 * means adding a line rather than editing three specs.
 *
 * `name` becomes a folder under `tests/visual-snapshots`, so it has to be
 * filesystem-safe and stable — renaming one orphans its baselines.
 */
export const pagesManifest = [
    { name: "landing", path: "/" },
    { name: "storybook", path: "/storybook" },
] as const;
