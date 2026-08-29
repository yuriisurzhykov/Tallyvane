# src — Feature-Sliced Design layers

Ordered from the top down. **A module may import only from layers strictly
below it**, and only through the importee's `index.ts`.

```
app       providers, global styles, registry composition
views     whole screens          (canonical FSD calls this layer "pages")
shared    the frontend-shared package: design system, API client, i18n, block contract, primitives
```

No local `widgets`, `features` or `entities` here (ADR-065): this app now
serves the public surface only, which renders `content-kit`'s blocks and
carries no product-domain slices of its own. The console's entities,
features and widgets — `job`, `pipeline-table`, `apply-to-job` and the rest —
moved to [`frontend-app`](../frontend-app/README.md) when it became a
separate application.

`shared` has no local directory here any more — it moved to the
`frontend-shared` workspace package (ADR-032) because `frontend-admin`, a
genuinely separate application, needs the same design system, API client and
string engine without depending on this app. Import it the same way either
app does: `frontend-shared/ui/theme`, not a relative path.

## Why `views` and not `pages`

Canonical FSD names this layer `pages`. In a Next.js project that word already
means routing, and two meanings for one word in the same tree is a bug waiting
to happen. This is the only deviation from canonical naming, and it is recorded
in ADR-022.

## Segments

Inside a slice, only these five directories are permitted:

```
ui/       components
model/    state, hooks, the logic of the scenario
api/      server calls
lib/      narrow, named helpers — never a `utils` bucket
config/   constants of the scenario
```

Anything else at the top level of a slice fails validation. Structure *inside*
a segment is free, which is why `shared/ui/theme/` with its own subtree is
legal.

## Rules that are checked, not agreed

Import direction. Public API only — `@/features/apply-to-job` is allowed,
`@/features/apply-to-job/ui/ApplyButton` is not. Slice isolation within a
layer. No cycles. No user-facing string literal in JSX. No raw colour or
dimension value where a token exists. `shared` contains no vocabulary from the
job-hunting domain.

The tooling is Steiger, `eslint-plugin-boundaries`, `dependency-cruiser` and a
project-specific validator; all of it runs behind one `arch` script.
