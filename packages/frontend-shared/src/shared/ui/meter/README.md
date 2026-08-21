# meter

A value within a range — Tier 0, per `COMPONENTS.md`'s "Status and feedback"
row, explicitly called out there as "semantically distinct from progress"
even though the two currently share a visual language. A thin wrapper
around `@base-ui/react/meter`: it knows tokens and a narrowed public
surface, nothing about which value or which range.

## What needed doing

The distinction `COMPONENTS.md` draws matters for a real reason beyond
naming: `Progress` describes a task moving toward completion (its value can
only sensibly increase, and reaching `max` means "done"). `Meter` describes
a position within a fixed range that says nothing about progress or
completion — "this offered salary sits 60% of the way through the target
range" is not "60% finished." Native HTML draws the same line
(`<progress>` versus `<meter>`, with different ARIA roles,
`progressbar`/`meter`), which is the reasoning this component's own
`role="meter"` — Base UI's default, unchanged — rests on.

## What was actually done

Built as `Progress`'s sibling, sharing its exact visual shape (see that
component's own README for the track/indicator reasoning, identical here),
but reading from `@base-ui/react/meter` instead — a separate compound API
in Base UI, not a mode flag on the same one, which is worth stating because
it means this component's structure had to be independently verified
against Meter's own `MeterRoot`/`MeterIndicator`/`MeterLabel`/`MeterValue`
source rather than assumed identical to `Progress`'s.

One real difference surfaced by that verification: Base UI's `Meter` has no
`value: null`/indeterminate mode at all (`MeterRootProps.value: number`,
required, non-nullable) — there is no "indeterminate meter" concept in the
first place, unlike `Progress`, so there was nothing to narrow away here the
way `Progress`'s own README describes doing deliberately.

A second thing checked rather than assumed: whether Base UI's `Meter`
implements the native `<meter>` element's `low`/`high`/`optimum`
sub-range attributes (the "this section of the range is bad/good"
banding some native meters use). It does not — verified against
`MeterRoot.d.ts`, which lists only `min`/`max`/`value`/`format`/`locale`/
`getAriaValueText`. This batch's one confirmed use case ("a value within a
range") does not need sub-range banding either, so nothing was added to
compensate for the gap; flagged here rather than silently building past
what was asked.

## Judgment calls made while building this component

- **Visually identical to `Progress`, deliberately, for now.** Nothing in
  the confirmed decisions asks the two to look different, and inventing a
  visual distinction with no stated reason would be guessing at a
  requirement rather than following one. If a real call site later needs
  Meter to read differently from Progress at a glance, that is a `tone` or
  a new visual decision for that call site to ask for explicitly.
- **No `tone`/`size` variant, no sub-range banding.** Both are the same
  "confirmed decision fixed the surface explicitly" case `Progress`'s
  README documents.
- **Track thickness as a borrowed spacing-scale class, not a new component
  token.** Same reasoning as `Progress`'s own README: no new
  `theme/components/` file for a value this batch never asked either
  component to own.

## SOLID

Single responsibility: tokens and a narrowed public surface over Base UI's
own compound API — nothing about what the value or the range mean.
Liskov-adjacent point worth naming even without a shared base type: a
caller reading `Meter`'s and `Progress`'s props side by side should not be
able to tell, from the API alone, that one models completion and the other
does not — the ARIA role is where that distinction actually lives, which is
exactly where native HTML also puts it.
