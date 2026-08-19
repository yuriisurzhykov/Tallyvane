# frontend-shared

The FSD `shared` layer — design tokens and theme, the API client, the string
engine, narrow `lib` helpers, environment `config`, and the content-block
registry *contract* (not the block types themselves, see `content-kit`) — held
as its own workspace package instead of living inside `frontend-web/src/shared`.

## What needed doing

`tallyvane.com` (blog), `app.tallyvane.com` (console) and `admin.tallyvane.com`
(CMS admin) were originally one Next.js application (ADR-011) with three route
groups. Making the admin surface a genuinely separate, network-isolated
application (ADR-032) meant `frontend-admin` had to be its own pnpm workspace
member with no dependency on `frontend-web` — otherwise "separate app" would only
be a separate `Host` header check over the same module graph, not a real
boundary a developer could not cross by accident.

That split immediately raises the question ADR-013 already answered once: the
theme, the API client and the string engine are needed by *both* apps, not
because they overlap in business meaning (they don't — `shared` holds none,
by the `shared-has-no-domain` rule) but because there is exactly one visual
brand and exactly one backend contract, and two independent copies of either
would drift the moment one of them was edited without the other.

## What was actually done

`frontend-web/src/shared/*` moved here verbatim — no file's content changed,
only import paths that crossed the new package boundary
(`frontend-web/scripts/generate-design-tokens.ts`, `frontend-web/scripts/contrast-table.ts`,
`frontend-web/app/layout.tsx`, and the equivalent files under `frontend-admin/`).
The package boundary matches the FSD layer boundary exactly, which is what
makes this a clean extraction rather than an awkward one: `shared`'s own rule
("holds no business identifiers") is precisely the property that made it safe
to hand to two independent apps without first negotiating what belongs to
which.

**What did not move here:** `entities/content-page`, `entities/media-asset`
and the block-type widgets (`block-renderer`, `hero-block`, ...). Those *do*
carry business meaning — the content domain — so they live in `content-kit`
instead, which depends on this package rather than being part of it. Folding
them in here would have forced every future non-content consumer of the
design system to also depend on the block-type system, for no reason other
than convenience at extraction time.

## Consuming it

```ts
import { ThemeProvider, ThemeInitScript } from "frontend-shared/ui/theme";
import { color } from "frontend-shared/ui/theme/tokens";
```

Both `frontend-web` and `frontend-admin` declare `"frontend-shared": "workspace:*"`
— the same mechanism `design-token-engine` already used before this split, so
no new resolution trick and no Turbopack tsconfig-`paths`-outside-project
trap (a real known failure mode for that shortcut, recorded in this repo's
development-methodology rule).

## Token generation stays two call sites, one destination

Each app keeps its own `scripts/generate-design-tokens.ts` (so each app's
build is self-sufficient and `tokens:check` runs as part of either app's own
`arch` script), but both scripts point `GENERATED_DIR`/`ADAPTERS_DIR` at
*this* package's `src/shared/ui/theme/{generated,adapters}` — there is one
compiled artifact, not two that could silently disagree. See
`frontend-web/scripts/generate-design-tokens.ts`'s own header comment for the
exact resolution.

## Architecture checks

This package is small enough to still be checked the same way `frontend-web` is:
`steiger` for the FSD methodology, `dependency-cruiser` for the import graph,
run through its own `pnpm run arch`. The `engine-is-build-time-only` rule is
copied from `frontend-web/.dependency-cruiser.cjs` with one path segment changed
(`../design-tokens/` instead of `../../packages/design-tokens/`, since this
package is one level closer to it) — the underlying reasoning, and the pnpm
symlink behaviour it depends on, is identical.
