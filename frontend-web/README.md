# Tallyvane — frontend-web

The public app: blog/landing/docs/changelog/legal pages, at `tallyvane.com`,
organised by strict Feature-Sliced Design. Neither the console
(`app.tallyvane.com`, [`frontend-app`](../frontend-app/README.md)) nor the CMS
admin (`admin.tallyvane.com`, [`frontend-admin`](../frontend-admin/README.md))
live here — all three used to be fewer applications than they are now
(ADR-011 had one; ADR-032 split admin off; ADR-065 split the console off too),
each time because a surface needed a property a route group inside a shared
process could not express. Full reasoning in [ARCHITECTURE.md](../ARCHITECTURE.md)
section 12 and
[docs/adr/ADR-032-subdomain-split-and-admin-isolation.md](../docs/adr/ADR-032-subdomain-split-and-admin-isolation.md) /
[docs/adr/ADR-065-console-is-a-third-application.md](../docs/adr/ADR-065-console-is-a-third-application.md).

The design system, API client, string engine and block-registry contract live
in the [`frontend-shared`](../packages/frontend-shared/README.md) package —
needed by all three applications, so it cannot live inside any one of them.
The content-page/media-asset entities and every content-block type live in
[`content-kit`](../packages/content-kit/README.md), needed by this app (public
render) and `frontend-admin` (editor/preview) — ADR-013 defines a block type
exactly once for both. Whether `frontend-app` needs it too is an open question,
tracked in ARCHITECTURE.md §12.5; nothing forces an answer before the console
has an actual content-block scenario.

## Two trees, and why

```
app/     Next.js App Router — routing only
src/     FSD layers — everything else
```

`app/` is **not** the FSD `app` layer. It is a routing adapter, and every file
in it is a single re-export:

```tsx
// app/(public)/blog/[slug]/page.tsx
export { BlogPostPage as default } from '@/views/blog-post';
```

The validator enforces this from both directions: a route file may not exceed
five lines, and no file named `page.tsx`, `layout.tsx`, `route.ts`,
`template.tsx`, `loading.tsx` or `error.tsx` may exist anywhere under `src/`.
That removes any ambiguity between the two meanings of "app".

## What this app renders

Every route here is server-rendered with tag-based caching, because search
engines have to see finished HTML — there is no client-rendered surface in
this app any more (ADR-065 moved the one that was, the console, to its own
application). Ktor calls this app's `/api/revalidate` webhook to mark tags
stale after a publish; the next visitor's request triggers a fresh server
fetch and render.

## Layers

Import direction is one way only, and always through a slice's `index.ts`.

| Layer | May import from | Holds |
| --- | --- | --- |
| `app` | everything below | Providers, global styles, registry composition |
| `views` | shared | Whole screens — `landing`, `blog-index`, `blog-post`, `doc-page`, `changelog`, `legal-page` |
| `shared` | nothing | The `frontend-shared` package — design system, generated API client, string engine, block contract |

No local `widgets`, `features` or `entities`: this app has no product domain
of its own, only pages that render what `content-kit` and `frontend-shared`
already carry. See [`src/README.md`](src/README.md).

`content-kit` is imported alongside `frontend-shared` wherever a public page
renders a content block, but it isn't itself an FSD layer of this app — it's
a workspace package with its own internal `entities` → `widgets` layering,
documented in [its own README](../packages/content-kit/README.md).

## The composition-root trick

Content-block types are slices of `content-kit`'s `widgets` layer, so by the
isolation rule `content-kit`'s own `block-renderer` cannot import them either.
It does not need to: the registry contract lives in `frontend-shared/blocks`,
the concrete blocks are assembled in this app's `app/providers` (and again,
identically, in `frontend-admin`'s), and the renderer reaches them through a
hook. That is the same dependency inversion the backend uses in its
composition root, and it lets FSD hold without a single exception — across a
package boundary as well as a slice boundary.

## Setup

`pnpm install` from the repository root — this app is one workspace member
among several (`packages/design-tokens`, `packages/frontend-shared`,
`packages/content-kit`, `frontend-app`, `frontend-admin`), and pnpm links the
others in as real dependencies rather than copies. The script names the
architecture refers to (`arch`, `tokens:generate`, `tokens:check`) are already
wired and fail loudly if a tool is missing, so that never looks like a
passing check.
