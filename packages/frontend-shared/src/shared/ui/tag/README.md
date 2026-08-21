# tag

A removable chip — Tier 0, per `COMPONENTS.md`'s "Status and feedback" row:
a tech tag or skill. This is what `MultiSelect` (a later batch, per
`COMPONENTS.md` §3's Inputs table) will render each selected value as, which
is why its API shape was fixed carefully even though nothing in this batch
consumes it yet: content as `children`, `onRemove` required, `tone`
optional.

## What needed doing

Something distinguishable from `Badge` on sight and by contract: `Badge` is
a passive status word with no way to remove it; `Tag` always hosts a real
dismiss affordance and is meant to be plucked out of a list one at a time.
Visually it also needed its own corner — `chip`, not `Badge`'s `pill` — so a
row of tags reads as a set of small, similar objects rather than a row of
tiny pill-badges that happen to have an X on them.

## What was actually done, including a corrected wrong turn

**First draft:** a dedicated `tagTokens` component-token file
(`theme/components/tag.ts`), mirroring `statusBadgeTokens` exactly —
`paddingX`/`paddingY`/`radius`, registered in `compiler.config.ts`,
regenerated. This is what "gets its own component tokens, distinct from
`statusBadgeTokens`" reads as most literally, and it is the same shape this
batch's own confirmed decision names.

**What building it surfaced:** the token compiler's own DS201 validation
failed the build the moment it existed — `pnpm --filter "./frontend-web"
run tokens:check` reported *"Primitive `dimension.1`/`dimension.2` crosses
component/composite domain boundaries"* between `component:statusBadge` and
`component:tag`, because both wanted the same "tight label padding" and
both had reached for the same raw primitives independently to say so. The
rule's own message states the fix precisely: *"promote to a global-semantic
role, OR keep both as independent tokens if this is coincidence, not shared
meaning."* It was not a coincidence — both components want the same
job done — and picking artificially different raw numbers just to make the
checker stop complaining would have manufactured a difference that does not
exist, exactly the kind of token drift this whole validation layer exists to
prevent.

**The correction:** deleted `theme/components/tag.ts` and its two
registrations, and had `Tag` read the *existing* semantic roles directly
instead — `rounded-chip` for the radius (already a real, required role in
`semantic/radius.ts`, confirmed by reading that file rather than assumed;
the "if none exists yet, this is a gap" contingency in this batch's brief
never materialized) and the shared `inline`/`inline-tight` spacing roles for
padding, the same roles every other Tier 0 primitive in this package already
reads directly for the identical job. Re-reading the confirmed decision
after this, its own phrasing supports this reading: it says to check
`semantic/radius.ts` "for the exact role name," which is exactly a semantic
role lookup, not an instruction to build a parallel component-token
indirection layer for a value that role already names cleanly. A
component-token file earns its place, per `statusBadgeTokens`' own doc
comment, for "a value belonging to exactly one component that would mislead
as a global name" — `chip`/`inline`/`inline-tight` are the opposite of
that: already-shared, non-misleading roles with other consumers across the
package. This batch's report documents this reversal explicitly rather than
quietly landing only the corrected version, per this repository's own
practice of recording a wrong turn rather than erasing it.

The dismiss button is hand-rolled rather than `IconButton`, following
`SearchField.tsx`'s own precedent and reasoning exactly: `IconButton` is a
square, `control`-height-sized real button (32px at its smallest) — the
right weight for a toggle beside a field, heavier than a chip only ~24px
tall can hold without the button dominating it. A real
`<button type="button">`, a required `removeLabel` (mirroring
`SearchField`'s `clearLabel` — Tier 0 owns no copy of its own), and a 16px
glyph plus `p-inline-tight` on every side for the WCAG 2.2 24×24 CSS px hit
target this project's component-authoring skill calls for at this scale —
not a shortcut past the accessibility work.

## Judgment calls made while building this component

- **No dedicated component-token file**, reversed from the first draft —
  reasoned above in detail, backed by a real, measured compiler failure
  rather than a stylistic preference.
- **`removeLabel` as a required prop**, rather than deriving an accessible
  name from `children` automatically. `children` is `ReactNode`, not
  guaranteed to be a plain string, so an automatic "Remove {children}"
  label is not always constructible — `SearchField`'s `clearLabel`
  precedent was followed instead of guessing at string coercion.
- **Full border plus wash**, one step past `Badge`'s own `subtle`
  treatment, since a tag is an interactive object with an edge you act on
  rather than a passive label — the same reasoning `Callout`'s README gives
  for its own, differently-motivated departure from `Badge`'s look.
- **Hand-rolled dismiss button over `IconButton`.** The confirmed decision
  explicitly left this as "your call, document it" — resolved above via
  `SearchField`'s own precedent rather than a fresh decision.

## SOLID

Single responsibility: a tone-coloured, removable chip — nothing about what
a "tech tag" or "skill" means, which is exactly why `tone` stays optional
and generic rather than encoding a specific vocabulary. Open/closed: a
sixth tone is a map entry; a new removal affordance shape would be a new
component, not a mode flag on this one. Dependency inversion, in the
smallest sense: this component depends on the existing `chip`/spacing roles'
public contract, not on how they resolve — exactly the property that made
correcting the first draft a one-file deletion rather than a rewrite.
