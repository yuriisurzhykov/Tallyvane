# Tallyvane — frontend

The "web" app: the public blog/landing/docs/changelog/legal pages
(`tallyvane.com`) and the console (`app.tallyvane.com`), organised by strict
Feature-Sliced Design. The CMS admin (`admin.tallyvane.com`) is a separate
application, [`frontend-admin`](../frontend-admin/README.md) — the two used to
be one Next.js app with three route groups (ADR-011), until ADR-032 made
admin a genuinely separate, network-isolated app so that no dependency edge
could let either side import the other's code by accident. Full reasoning in
[ARCHITECTURE.md](../ARCHITECTURE.md) section 12 and
[docs/adr/ADR-032-subdomain-split-and-admin-isolation.md](../docs/adr/ADR-032-subdomain-split-and-admin-isolation.md).

The design system, API client, string engine and block-registry contract live
in the [`frontend-shared`](../packages/frontend-shared/README.md) package —
needed by this app and by `frontend-admin` both, so it cannot live inside
either. The content-page/media-asset entities and every content-block type
live in [`content-kit`](../packages/content-kit/README.md), for the same
reason plus one more: ADR-013 defines a block type exactly once for both the
public render (here) and the admin editor/preview (`frontend-admin`).

## Two trees, and why

```
app/     Next.js App Router — routing only
src/     FSD layers — everything else
```

`app/` is **not** the FSD `app` layer. It is a routing adapter, and every file
in it is a single re-export:

```tsx
// app/(console)/today/page.tsx
export { TodayPage as default } from '@/views/today';
```

The validator enforces this from both directions: a route file may not exceed
five lines, and no file named `page.tsx`, `layout.tsx`, `route.ts`,
`template.tsx`, `loading.tsx` or `error.tsx` may exist anywhere under `src/`.
That removes any ambiguity between the two meanings of "app".

## Two surfaces in this app

`(public)` renders on the server with tag-based caching, because search engines
have to see finished HTML. `(console)` is client-rendered and talks to the
Ktor API directly from the browser — server rendering buys nothing behind a
login, and going straight to the API keeps that API honest for the mobile
client that comes later, and for `frontend-admin`, which talks to the same
Ktor API the same way.

## Layers

Import direction is one way only, and always through a slice's `index.ts`.

| Layer | May import from | Holds |
| --- | --- | --- |
| `app` | everything below | Providers, global styles, registry composition |
| `views` | widgets and below | Whole screens |
| `widgets` | features and below | Self-contained composite blocks (console-only; content-block widgets live in `content-kit`) |
| `features` | entities and below | User scenarios |
| `entities` | shared | Business entities (console-only; `content-page`/`media-asset` live in `content-kit`) |
| `shared` | nothing | The `frontend-shared` package — design system, generated API client, string engine, block contract |

`content-kit` is imported alongside `frontend-shared` wherever a public page
renders a content block, but it isn't itself an FSD layer of this app — it's
a workspace package with its own internal `entities` → `widgets` layering,
documented in [its own README](../packages/content-kit/README.md).

Slices within a layer do not see each other. The single exception is
cross-imports between entities through the `@x` notation, where the slice being
imported declares what it opens and to whom.

## The composition-root trick

Content-block types are slices of `content-kit`'s `widgets` layer, so by the
isolation rule `content-kit`'s own `block-renderer` cannot import them either.
It does not need to: the registry contract lives in `frontend-shared/blocks`,
the concrete blocks are assembled in this app's `app/providers` (and again,
identically, in `frontend-admin`'s), and the renderer reaches them through a
hook. That is the same dependency inversion the backend uses in its
composition root, and it lets FSD hold without a single exception — now
across a package boundary as well as a slice boundary.

## Setup

`pnpm install` from the repository root — this app is one workspace member
among several (`packages/design-tokens`, `packages/frontend-shared`,
`packages/content-kit`, `frontend-admin`), and pnpm links the others in as
real dependencies rather than copies. The script names the architecture
refers to (`arch`, `tokens:generate`, `tokens:check`) are already wired and
fail loudly if a tool is missing, so that never looks like a passing check.
