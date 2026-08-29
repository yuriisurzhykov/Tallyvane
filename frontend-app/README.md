# Tallyvane — frontend-app

The console: `app.tallyvane.com`. Today's actions, the pipeline, job briefs,
contacts, resume studio, analytics, settings — the private, authenticated
surface a job search actually runs from, organised by strict Feature-Sliced
Design.

## Why this is a separate application, not a route group inside `frontend-web`

It used to be a route group inside `frontend-web` (ADR-011), sharing that
app's process because the reasoning at the time held: a Node runtime was
needed for public SSR anyway, so a second one for the console bought
nothing. [ADR-065](../docs/adr/ADR-065-console-is-a-third-application.md)
changed that once the console got its own reasons to run a server — partial
SSR/SSG for a first paint, and Server-Sent Events for live updates — reasons
that don't disappear if `frontend-web` disappears, unlike ADR-011's original
console. A shared container also means a shared `mem_limit`: an SSE-heavy
console session and a cache-miss storm on the public blog would compete for
the same ceiling, which `docker compose`'s per-service memory isolation can't
help with if they're one process.

## Two trees, same convention as the sibling apps

```
app/     Next.js App Router — routing only, one re-export per file
src/     FSD layers — everything else
```

No `Host`-based `proxy.ts` here, unlike what ADR-011 originally specified:
nginx already resolves which container a hostname reaches before the request
gets this far (ADR-065), so there is nothing left for an in-app proxy to
decide.

## What's shared with the sibling apps, and how

- **`frontend-shared`** — the design system, API client and string engine.
  Same package, same generated CSS (`app/globals.css` imports the identical
  two files the sibling apps do), so all three apps render one consistent
  brand rather than three that could drift.
- **`content-kit`** — **not currently a dependency.** `frontend-web` and
  `frontend-admin` both need it (public render, editor/preview); whether the
  console ever will is open (ARCHITECTURE.md §12.5) — nothing in the console's
  current scope renders a content block. Adding it back is a `package.json`
  line plus `transpilePackages`/`.dependency-cruiser.cjs` entries, not an
  architectural decision, whenever a real scenario shows up.

## Server-Sent Events

The console's live-update connection (`EventSource`) goes straight to Ktor —
`app.tallyvane.com/api/*`, same-origin, same path every other console request
already takes (ADR-012, unchanged) — and never touches this app's process.
See ADR-065 for the two conditions that make that reliable behind Cloudflare's
tunnel: a heartbeat from Ktor more often than the proxy's 100-second idle
timeout, and `proxy_buffering off` on that nginx location.

## Layers

| Layer | May import from | Holds |
| --- | --- | --- |
| `app` | everything below | Providers, global styles, registry composition |
| `views` | widgets and below | `today`, and the rest of ARCHITECTURE.md §12.5's console views as they get built |
| `widgets` | features and below | Self-contained composite blocks — `pipeline-table`, `today-actions`, and the rest |
| `features` | entities and below | User scenarios — `capture-job`, `apply-to-job`, and the rest |
| `entities` | shared | Business entities — `job`, `company`, `application`, and the rest |
| `shared` | nothing | The `frontend-shared` package |

Unlike `frontend-admin`, this app does have a local `entities` layer: the
console's domain nouns (`job`, `application`, `contact`, …) are not content
entities `content-kit` already owns, they're this product's own subject
matter.

## Status

A skeleton with one real route (`today`), matching how far the other two
apps have been built. `(console)`'s remaining seven routes from
ARCHITECTURE.md §12.2 — `pipeline`, `jobs/[id]`, `brief/[applicationId]`,
`contacts`, `resume`, `analytics`, `settings` — are documented as the target
shape there, not yet scaffolded here; adding them is frontend feature work,
not part of standing this application up for deployment.
