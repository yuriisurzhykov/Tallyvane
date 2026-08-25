# storybook

A Storybook instance showing Tier 0+ components in isolation, held as its own
workspace package rather than living inside `frontend-shared`.

## What needed doing

Tier 0 primitives need a way to be seen and checked in every documented
variant — `Button` in `primary`/`neutral`/`ghost`/`danger`, `Field` with and
without an error — without either hand-writing Next.js pages that grow
without bound, or reaching for a third-party visual-regression wrapper. Two
candidates (`storywright`, `lost-pixel`) were checked and rejected: the first
has roughly two GitHub stars, too unproven for CI infrastructure; the
second's repository is archived and its own README now points users at a
paid SaaS. Full comparison in
[docs/frontend/02-component-testing-architecture.md](../../docs/frontend/02-component-testing-architecture.md).

## What was actually done

Real Storybook (`storybook` + `@storybook/react-vite`, both v10), reading
`.stories.tsx` files colocated with each component in `frontend-shared` — no
hand-written pages, nothing to register by hand. `@storybook/react-vite`
rather than `@storybook/nextjs-vite`: nothing in scope (Tier 0) touches
`next/navigation`, `next/image` or `next/font`, so the Next-specific adapter
would only add mocking machinery for APIs nothing here calls. `.storybook/main.ts`
names exactly this reasoning and the exact place to revisit it.

`.storybook/preview.tsx` wraps every story in the real `ThemeProvider`, driven
by a toolbar toggle calling its actual `setPreference` — not a simplified
class swap — so a component previewed here behaves like it does in a real
app. `.storybook/preview.css` reproduces `frontend-web/app/globals.css`'s
three-line import chain verbatim, in the same order, because the order is
load-bearing (see that file's own comment).

Checking logic is not reimplemented here. `tests/e2e/story-manifest.ts` reads
Storybook's own built `storybook-static/index.json` — the list Storybook
already produces, not one maintained by hand — and turns it into the
`{ name, path }[]` shape `test-kit`'s spec functions take:

```ts
import { defineA11ySpecs } from "test-kit/specs/a11y";
import { readStoryManifest } from "./story-manifest";
defineA11ySpecs(readStoryManifest(), { surface: "component" });
```

The same `defineA11ySpecs`/`defineWcagContrastSpecs`/`defineApcaContrastSpecs`/
`defineVisualSpecs` that check `frontend-web`'s pages check every story here.
The a11y call is the one exception that is not a bare list: stories are not
pages, so `{ surface: "component" }` skips the page-scoped axe rules (owned
in `test-kit`, not listed here) instead of wrapping every iframe in a fake
`<main>`/`<h1>`. See `packages/test-kit/README.md` for why the rest of the
checking logic lives neither there nor here.

**One sequencing detail worth stating plainly**: Storybook has to be built
*before* `playwright test` even starts, not inside its `webServer`.
`story-manifest.ts` reads `storybook-static/index.json` synchronously while
Playwright is still loading `.spec.ts` files, which happens before any
`webServer` starts — so the `test:*` scripts run
`scripts/ensure-storybook-static.mjs` as a distinct, earlier step, and
`webServer` here only serves the already-built output via `http-server`.
The ensure step skips `storybook build` when `storybook-static/index.json`
is newer than every Storybook input (`.storybook/`, this package's own
config, `frontend-shared/src`, the workspace `pnpm-lock.yaml`, and the
repo-root `package.json`). Deleting or renaming a story counts: the walk
uses the parent directory's mtime, because that is what changes when no
newer file is left behind. Playwright specs under `tests/` are not
inputs — neither are `*.md` / `*.test.*` / `*.spec.*` inside
`frontend-shared`, which do not feed the iframe. CI still always rebuilds
(`CI` is set; the folder is gitignored and not cached). `pnpm run
build-storybook` still force-rebuilds.

## What did not move here

The components themselves — they live in `frontend-shared`, imported as a
normal workspace dependency, the same way `frontend-web` and `frontend-admin`
already do. This package holds only the Storybook configuration and its own
Playwright suite.

## Consuming it

```bash
pnpm --filter "./packages/storybook" run storybook        # dev server, :6006
pnpm --filter "./packages/storybook" run build-storybook   # static output for CI
pnpm --filter "./packages/storybook" run test:a11y         # ensures static output, then checks every story
```

Not part of the root `pnpm verify`/`pnpm test` fan-out, by the same
convention `frontend-web`'s own Playwright suite already follows: neither
package names a plain `"test"` script, so `pnpm --recursive --if-present run
test` never reaches either. Both run from their own dedicated CI workflow
instead, on their own schedule.

## 2026-08-25 — `test:*` rebuilt Storybook even when only a spec changed

`test:scoped` exists so local iteration on one story does not cost a full CI
suite (`run-scoped.mjs`'s own header). Its `package.json` script still
unconditionally ran `build-storybook` first, so a Playwright-only edit
(typeahead focus, say) paid a full Vite emit — minutes — for an iframe that
`http-server` already had on disk. `storybook build` has no per-story static
output, so the granularity we actually have is skip vs rebuild: skip when
`storybook-static/index.json` is newer than `.storybook/`, this package's
config, `frontend-shared/src`, the workspace lockfile, and the repo-root
`package.json`; rebuild when any of those changed, when a story was
deleted or renamed (the directory mtime moves), when the output is
missing, or when `CI` is set. Specs under `tests/` are not
inputs, and neither are markdown or `*.test.*` files inside
`frontend-shared`. `pnpm run build-storybook` remains the force path.

## 2026-08-25 — skip vs rebuild missed deletions and lockfile-only updates

Two holes in the freshness walk, both found by review rather than by a
failing suite.

`walkForStale` compared only file mtimes. Deleting or renaming a story
updates the parent directory's mtime and leaves no newer file, so
`decideRebuild` skipped and Playwright served HTML that still contained
the removed story. The walk now treats a directory newer than
`index.json` as stale too. Editing an existing `.md` / `*.test.*` file
does not bump the directory; adding, deleting or renaming one does, and
that extra rebuild is accepted — a wasted `storybook build`, not a stale
iframe.

`node_modules` is skipped on purpose, but the walk also ignored
`pnpm-lock.yaml` and the repo-root `package.json` (where `pnpm.overrides`
live). A lockfile-only bump of Storybook or Tailwind plus `pnpm install`
left every listed input untouched, so tests ran against assets built with
the previous graph. Those two files are inputs now; a missing lockfile
is ENOENT and does not force a rebuild.

## Growing beyond Tier 0

Adding `frontend-web/src/**/*.stories.tsx` or `frontend-admin/src/**/*.stories.tsx`
coverage later means widening the `stories` glob in `.storybook/main.ts` and
adding the matching workspace dependency here — nothing about `test-kit`, the
Playwright config, or `story-manifest.ts` changes, because none of them know
which package a story came from.
