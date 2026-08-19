# Tallyvane — frontend

One Next.js application serving three surfaces, organised by strict
Feature-Sliced Design. Full reasoning in [ARCHITECTURE.md](../ARCHITECTURE.md)
section 12.

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

## Three surfaces, one application

`(public)` renders on the server with tag-based caching, because search engines
have to see finished HTML. `(console)` and `(admin)` are client-rendered and
talk to the Ktor API directly from the browser — server rendering buys nothing
behind a login, and going straight to the API keeps that API honest for the
mobile client that comes later.

## Layers

Import direction is one way only, and always through a slice's `index.ts`.

| Layer | May import from | Holds |
| --- | --- | --- |
| `app` | everything below | Providers, global styles, registry composition |
| `views` | widgets and below | Whole screens |
| `widgets` | features and below | Self-contained composite blocks, and one slice per content-block type |
| `features` | entities and below | User scenarios |
| `entities` | shared | Business entities |
| `shared` | nothing | Design system, generated API client, string engine, block contract |

Slices within a layer do not see each other. The single exception is
cross-imports between entities through the `@x` notation, where the slice being
imported declares what it opens and to whom.

## The composition-root trick

Content-block types are slices of `widgets`, so by the isolation rule the
renderer cannot import them. It does not need to: the registry contract lives
in `shared/blocks`, the concrete blocks are assembled in `app/providers`, and
the renderer reaches them through a hook. That is the same dependency inversion
the backend uses in its composition root, and it lets FSD hold without a single
exception.

## Setup

Nothing is installed yet — `package.json` declares no dependencies. The script
names the architecture refers to (`arch`, `tokens:generate`, `tokens:check`)
are already there and fail loudly, so a missing tool never looks like a passing
check.

Note: the architecture text says `pnpm`, but only npm is present on this
machine. Either works; pick one before the first install so the lockfile does
not have to be regenerated.
