import { fileURLToPath } from "node:url";
import type { NextConfig } from "next";

/**
 * Every workspace package this app imports raw TypeScript source from, rather
 * than a pre-built `dist/`. pnpm links a workspace dependency as a real
 * directory (see `frontend-shared/.dependency-cruiser.cjs`'s own comment on
 * this), so without `transpilePackages` Next would try to ship that source
 * unprocessed and fail the moment it hit a `.tsx` file or a bare `{path}`
 * import assertion the bundler doesn't recognise.
 *
 * `fileURLToPath`, not `new URL(...).pathname` directly — see
 * `frontend-web/next.config.ts`'s identical comment for the Windows
 * drive-letter failure this avoids.
 */
const workspaceRoot = fileURLToPath(new URL("..", import.meta.url));

const config: NextConfig = {
    transpilePackages: ["design-token-engine", "frontend-shared", "content-kit"],
    output: "standalone",
    outputFileTracingRoot: workspaceRoot,
    turbopack: { root: workspaceRoot },
    // Works around the same open Next.js 16.3.1 bug as the `@swc/helpers`/`@next/env`
    // dependency pins in package.json — see frontend-web/next.config.ts's identical
    // comment; verified here too, not assumed to carry over just because the version
    // matches.
    outputFileTracingIncludes: {
        "/*": [
            "./node_modules/@swc/helpers/**/*",
            "./node_modules/@next/env/**/*",
            // See frontend-web/next.config.ts's identical comment: react/react-dom are
            // direct dependencies, physically present under node-linker=hoisted, and the
            // tracer still omits them — verified here too, not assumed to carry over.
            "./node_modules/react/**/*",
            "./node_modules/react-dom/**/*",
        ],
    },
    // See frontend-web/next.config.ts's identical comment: `sharp` is an unused
    // optionalDependency of `next`, accounted for most of this image's size, and is
    // removed in the Dockerfile after the tracer's own exclusion option failed to touch
    // its native libraries through three different glob shapes.
};
export default config;