import type { NextConfig } from "next";

/**
 * Every workspace package this app imports raw TypeScript source from, rather
 * than a pre-built `dist/`. pnpm links a workspace dependency as a real
 * directory (see `frontend-shared/.dependency-cruiser.cjs`'s own comment on
 * this), so without `transpilePackages` Next would try to ship that source
 * unprocessed and fail the moment it hit a `.tsx` file or a bare `{path}`
 * import assertion the bundler doesn't recognise.
 *
 * `content-kit` is deliberately not listed: this app does not depend on it
 * yet (ARCHITECTURE.md §12.5's open question). Add it here in the same
 * change that adds it to `package.json`, not before.
 */
const workspaceRoot = new URL("..", import.meta.url).pathname;

const config: NextConfig = {
    transpilePackages: ["design-token-engine", "frontend-shared"],
    output: "standalone",
    outputFileTracingRoot: workspaceRoot,
    turbopack: { root: workspaceRoot },
};
export default config;
