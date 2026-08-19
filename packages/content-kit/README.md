# content-kit

The content-page/media-asset entities and every content-block widget, held as
their own workspace package instead of living inside `frontend-web/src`.

## What needed doing

Splitting `frontend-admin` out as a genuinely separate application (ADR-032)
ran straight into ADR-013: a content-block type is defined exactly once — one
file holding a schema, field descriptors, defaults and a component — and that
one definition already had to serve three consumers before this split (the
public page render, the admin editor's generated form, and the admin editor's
live preview). Two of those three consumers are now in *different
applications*. Keeping the block-renderer and the six block-type widgets
inside `frontend-web/src/widgets` would have meant `frontend-admin` either
duplicated them (exactly what ADR-013 rejected once already, for a different
reason) or depended on the whole of `frontend-web` to reach a handful of files —
defeating the point of giving admin its own dependency graph at all.

## What was actually done

`entities/content-page`, `entities/media-asset`, `widgets/block-renderer` and
the six `widgets/*-block` slices are scaffolded here as their own package,
depending on `frontend-shared` (for the design system and the block-registry
*contract* — the interface, not the concrete blocks) rather than being folded
into it. That distinction is deliberate: `frontend-shared` holds zero business
meaning by the `shared-has-no-domain` rule, and the content-block system is
squarely business meaning (the content domain) — merging the two packages
would have forced a future non-content consumer of the design system to pull
in the block-type system for no reason.

**Currently near-empty.** Only the `content` backend module's data model
existed as a stub (`shared/blocks/index.ts`, itself moved into
`frontend-shared`) before this split, so there is nothing to migrate here yet
beyond establishing the boundary — every slice is a placeholder `export {}`
with a comment pointing at ADR-013, ready for the content module's real
implementation to land directly in the right package instead of in
`frontend-web/src` and needing to move again later.

## Consuming it

```ts
import { } from "content-kit/widgets/block-renderer";
import { } from "content-kit/entities/content-page";
```

Both `frontend-web` (renders blocks read-only, on the server) and
`frontend-admin` (builds the edit form from the same field descriptors and
renders the same component for live preview) declare
`"content-kit": "workspace:*"`.

## Layers

Only two, because that's the entire overlap between the two apps:

```
entities   content-page, media-asset — read by both apps, written only by admin
widgets    block-renderer, and one slice per content-block type
```

Import direction is the same rule as everywhere else in this repo: `widgets`
may import from `entities`, never the reverse, and slices within a layer do
not see each other — `block-renderer` reaches a concrete block only through
the registry contract in `frontend-shared/blocks`, never by importing it
directly, so the composition root (each app's own `app/providers`) stays the
only place that knows every block type exists.
