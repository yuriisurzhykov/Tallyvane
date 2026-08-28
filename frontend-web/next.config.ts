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
 * `fileURLToPath`, not `new URL(...).pathname` directly: on Windows the raw
 * `pathname` of a `file://` URL keeps a leading slash before the drive letter
 * (`/E:/...`), which Next then fails to canonicalize
 * ("Синтаксическая ошибка в имени файла... os error 123") the moment
 * `next build` runs on this host rather than inside a Linux container.
 * `fileURLToPath` is the one conversion Node documents as handling every
 * platform's drive-letter/UNC quirks, so it is used here rather than a
 * string replace that would only patch the one shape actually observed.
 */
const workspaceRoot = fileURLToPath(new URL("..", import.meta.url));

const config: NextConfig = {
    transpilePackages: ["design-token-engine", "frontend-shared", "content-kit"],
    output: "standalone",
    outputFileTracingRoot: workspaceRoot,
    turbopack: { root: workspaceRoot },
    // Works around the same open Next.js 16.3.1 bug the `@swc/helpers`/`@next/env`
    // dependency pins in package.json exist for (vercel/next.js#97358): even hoisted to a
    // real, resolvable `node_modules/` entry, the standalone file tracer still does not
    // follow `next/dist`'s dynamic `require()` calls into either — verified locally, in a
    // container with NODE_ENV=production (the `@next/env` require only fires under that
    // condition, which is exactly the condition a real deployment always runs under, so a
    // check without it would have passed for the wrong reason). Delete this and both
    // dependency pins together once upstream ships a fix and a plain build still boots
    // without any of them.
    outputFileTracingIncludes: {
        "/*": [
            "./node_modules/@swc/helpers/**/*",
            "./node_modules/@next/env/**/*",
            // Unlike the two above, `react` is a direct dependency this app already
            // declares — switching pnpm to `node-linker=hoisted` (.npmrc) made it a real,
            // physically-present directory rather than a symlink, and the tracer still
            // does not include it. `next/dist/server/node-environment-extensions/*`
            // requires it during server startup (not per-request), so its absence is a
            // boot-time crash, not a lazily-discovered one — verified locally.
            "./node_modules/react/**/*",
            "./node_modules/react-dom/**/*",
        ],
    },
    // `sharp` is an optionalDependency of `next` itself (for `next/image`'s native
    // optimizer), not of this app — nothing here imports `next/image` yet (checked: zero
    // matches repo-wide). `node-linker=hoisted` plus this repo's broad
    // `supportedArchitectures` (added for Vitest's native bindings, not for this) installs
    // every platform's prebuilt binary as real files, and the standalone tracer copies
    // most of them: measured, `sharp` and its `@img/sharp-*` platform variants alone
    // accounted for roughly 700 MB of a 975 MB image. `outputFileTracingExcludes` was
    // tried first and abandoned: three glob shapes each measured against the real output,
    // and the `@img/sharp-libvips-*` native libraries specifically stayed at full size
    // through all three — Next's tracer appears to treat them as always-included
    // regardless of this option. The Dockerfiles delete them by path instead, after
    // copying out of the builder stage; see their comment for why that is more reliable
    // than continuing to negotiate with the tracer. Remove both this comment and the
    // Dockerfile deletion together the day this app's first `next/image` usage lands.
};

export default config;