# The frontend-admin image: the CMS admin (admin.tallyvane.com), standalone-built.
#
# Structurally identical to frontend-web/Dockerfile and frontend-app/Dockerfile — same
# base image, same reasoning at every step — because these are three applications, not
# three designs. See that file for the comments this one deliberately does not repeat.
# The build context is the repository ROOT. Build with:
#
#     docker build -f frontend-admin/Dockerfile -t tallyvane-frontend-admin .

FROM node:24-alpine AS base
RUN corepack enable

FROM base AS deps
WORKDIR /workspace
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
RUN pnpm --filter "./frontend-admin" run build

# See frontend-web/Dockerfile's comment on this exact line for why: pnpm's workspace
# symlinks in .next/standalone break across this stage boundary unless dereferenced here,
# while the originals this app's own build produced are still reachable.
RUN cp -rL frontend-admin/.next/standalone /tmp/standalone

# `sharp` cleanup, after `cp -rL` and not before, and matching both directory-naming
# shapes `cp -rL`'s dereferencing produces: see frontend-web/Dockerfile's identical
# comment for the full story.
RUN find /tmp/standalone -type d \( -iname 'sharp*' -o -iname '@img+sharp*' \) -prune -exec rm -rf {} +

FROM base AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

RUN addgroup --system --gid 1001 nodejs \
  && adduser --system --uid 1001 nextjs

# No public/ directory here yet either — see frontend-web/Dockerfile's comment on the
# equivalent line for why that COPY is absent rather than forgotten.
COPY --from=builder --chown=nextjs:nodejs /tmp/standalone ./
COPY --from=builder --chown=nextjs:nodejs /workspace/frontend-admin/.next/static ./frontend-admin/.next/static

USER nextjs
EXPOSE 3000

CMD ["node", "frontend-admin/server.js"]
