# Tallyvane — admin

The CMS admin app: `admin.tallyvane.com`. Manages the pages, media library and
editable strings that [`frontend-web`](../frontend-web/README.md) renders publicly at
`tallyvane.com`.

## Why this is a separate application, not a route group

It used to be a third route group inside `frontend-web` (ADR-011). ADR-032
changed that: the requirement was for admin to be network-isolated behind
Cloudflare Access, and for a developer to be structurally unable to import
console-only code into admin or vice versa. A `Host`-header check inside a
shared build would have gated *requests*, not code — the console's module
graph would still have contained every admin route handler, just unreachable
by the normal path. Only a real workspace boundary (`frontend-admin` never
depends on `frontend-web`, so the import cannot resolve at all) delivers that.

## Two trees, same convention as `frontend-web`

```
app/     Next.js App Router — routing only, one re-export per file
src/     FSD layers — everything else
```

## What's shared with `frontend-web`, and how

- **`frontend-shared`** — the design system, API client, string engine and
  block-registry contract. Same package, same generated CSS
  (`app/globals.css` imports the identical two files `frontend-web`'s does),
  so the two apps render one consistent brand rather than two that could
  drift.
- **`content-kit`** — the content-page/media-asset entities and every
  content-block widget. ADR-013 defines a block type exactly once; this app
  uses that one definition to build the edit form (from its field
  descriptors) and render the live preview (the same component `frontend-web`
  uses to render the block publicly).

Nothing else is shared. This app has no local `entities` layer at all — every
domain noun it needs (`content-page`, `media-asset`) already lives in
`content-kit`.

## Layers

| Layer | May import from | Holds |
| --- | --- | --- |
| `app` | everything below | Providers, global styles, registry composition |
| `views` | widgets and below | `admin-page-list`, `admin-page-editor`, `admin-media`, `admin-strings` |
| `widgets` | features and below | `block-editor` (built on `content-kit`'s `block-renderer`) |
| `features` | entities and below | `edit-page-blocks`, `publish-page`, `restore-revision`, `upload-media`, `edit-strings` |

## Network isolation

`admin.tallyvane.com` keeps a public DNS record but sits behind Cloudflare
Access, checked before any request reaches this app or the Ktor API behind
it. Sessions are scoped to this hostname only — logging into `app.tallyvane.com`
does not grant access here, and vice versa. See
[docs/adr/ADR-032-subdomain-split-and-admin-isolation.md](../docs/adr/ADR-032-subdomain-split-and-admin-isolation.md)
for the full reasoning, including what was rejected (a VPN instead of
Cloudflare Access; an env-var-gated single build instead of a real second
app).

## Status

Currently a skeleton: every view is a placeholder, matching how far
`frontend-web` itself has been built. The point of scaffolding it now, before
either app has real features, is that the workspace boundary exists from the
first line of admin-specific code rather than being retrofitted later.
