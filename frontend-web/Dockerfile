# The frontend-web image: the public Next.js app (tallyvane.com), standalone-built.
#
# The build context is the repository ROOT, not this directory: pnpm validates
# `pnpm-lock.yaml` against every workspace member `pnpm-workspace.yaml` names, not just
# this app's own dependency graph, and this app's `transpilePackages` pulls in raw
# TypeScript from sibling packages by real filesystem path. Build with:
#
#     docker build -f frontend-web/Dockerfile -t tallyvane-frontend-web .

FROM node:24-alpine AS base
RUN corepack enable

FROM base AS deps
WORKDIR /workspace

# Manifests only, in dependency order, so this layer is cached across every build that
# doesn't touch a package.json or the lockfile — copying source here would invalidate it
# on every change instead. Every workspace member gets a line, not just the ones this
# app imports: `--frozen-lockfile` below validates the lockfile against the whole
# workspace glob in pnpm-workspace.yaml, and a missing manifest for a member the lockfile
# knows about fails the install, not just a warning.
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY frontend-web/package.json frontend-web/
COPY frontend-app/package.json frontend-app/
COPY frontend-admin/package.json frontend-admin/
COPY packages/design-tokens/package.json packages/design-tokens/
COPY packages/frontend-shared/package.json packages/frontend-shared/
COPY packages/content-kit/package.json packages/content-kit/
COPY packages/test-kit/package.json packages/test-kit/
COPY packages/storybook/package.json packages/storybook/
RUN pnpm install --frozen-lockfile

FROM deps AS builder
WORKDIR /workspace
COPY . .

# Turbopack is the default bundler as of Next.js 16 — this app's `build` script carries
# no `--webpack` flag. `outputFileTracingRoot`/`turbopack.root` in next.config.ts already
# point at this workspace root, which is what lets tracing see frontend-shared and
# content-kit instead of stopping at this app's own directory.
RUN pnpm --filter "./frontend-web" run build

# `.next/standalone` traces only the runtime files this app actually needs — a fraction
# of the full workspace install — but pnpm links workspace and hoisted npm dependencies
# as symlinks, and the tracer copies that symlink structure into `.next/standalone`
# as-is. A symlink built here, at /workspace/frontend-web/.next (three `../` segments to
# reach the hoisted node_modules two levels up), breaks the moment the runner stage below
# copies it to a shallower path — the exact failure tracked at vercel/next.js#89851 for
# any monorepo doing a standard multi-stage Docker build. `cp -rL` dereferences every
# symlink into a real file while the originals are still reachable, here in the builder
# stage where the fix is cheap, producing a tree that is genuinely self-contained rather
# than one that only looks it until it's moved.
RUN cp -rL frontend-web/.next/standalone /tmp/standalone

# `sharp` is next's own optionalDependency for `next/image`; nothing in this app calls
# `next/image` yet. `outputFileTracingExcludes` in next.config.ts was tried first and
# abandoned — measured against the real output through three glob shapes, and the
# `@img/sharp-libvips-*` native libraries specifically never shrank through any of them.
# Deleting instead of excluding, and only after `cp -rL` above rather than before: deleting
# first left dangling symlinks elsewhere in the tree pointing at the now-missing
# directories, and `cp -rL` refuses to follow a symlink to nothing — measured, that
# ordering failed the build outright. Deleting the already-dereferenced copies in
# /tmp/standalone has nothing left to dangle. ~700 MB of a ~975 MB image, gone, confirmed
# by the image's size before and after this line existed rather than assumed from the
# deletion succeeding. Revert this the day this app's first `next/image` usage lands —
# that is also the day the size this removes stops being free.
#
# Two directory-naming shapes, not one, hold the same content: `cp -rL` dereferencing a
# symlink means one real package can exist twice on disk under two different names —
# pnpm's own flat store naming for the top level (`.pnpm/@img+sharp-libvips-linux-x64@1.3.2`,
# basename starts with `@img+sharp`) and a normal scoped nesting one level further in
# (`.pnpm/node_modules/@img/sharp-libvips-linux-x64`, basename starts with `sharp`).
# Measured: a `sharp*`-only pattern left the first shape's 700 MB untouched; an
# `@img+sharp*`-only pattern left the second shape's 108 MB copy untouched. Both together
# is what actually emptied both.
RUN find /tmp/standalone -type d \( -iname 'sharp*' -o -iname '@img+sharp*' \) -prune -exec rm -rf {} +

FROM base AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# No --max-old-space-size here. Node reads the container's own cgroup memory limit and
# sizes the default heap from it — set by `mem_limit` in ops/docker-compose.yml, not
# here — and an explicit flag would turn that auto-adjustment off for whichever half of
# the heap it names. A number picked without a real container in front of it is a guess
# that outlives the guess; ARCHITECTURE.md §16.2 already carries this exact mistake's
# JVM version, dated, as the reason `-XX:MaxRAMPercentage` and its container limit are
# kept in the same place instead of one of them here.
RUN addgroup --system --gid 1001 nodejs \
  && adduser --system --uid 1001 nextjs

# The standalone tree already nests under frontend-web/ — outputFileTracingRoot points
# at the workspace root, one level above this app, so tracing mirrors that one level of
# path into the output. `.next/static` and `public/` are deliberately excluded from
# `.next/standalone` (Next.js's own choice — see the `output` docs) on the assumption a
# CDN serves them; this deployment has no CDN of its own in front of Next, so both are
# copied by hand. frontend-web has no public/ directory yet — add that COPY line here
# the day one exists, or a rebuild will start failing on a missing source with a message
# that already explains why.
COPY --from=builder --chown=nextjs:nodejs /tmp/standalone ./
COPY --from=builder --chown=nextjs:nodejs /workspace/frontend-web/.next/static ./frontend-web/.next/static

USER nextjs
EXPOSE 3000

CMD ["node", "frontend-web/server.js"]
