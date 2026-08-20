# fieldset

Tier 0 — a group of controls under one legend, with the legend's
accessible-name wiring handled for the group as a whole. The group-level
counterpart to `Field`'s single-control wiring.

## What needed doing

A set of related controls — a block of checkboxes, a set of radio options —
needs one accessible name for the *group*, not just individually-labelled
controls sitting next to each other with no shared context a screen reader
can announce. That's a different ARIA relationship than `Field` solves (one
label, one control): `Fieldset` is `aria-labelledby` pointing at a legend
for a `role="group"`, not `for`/`id` pointing at one input.

## What was actually done

A thin styling wrapper over `@base-ui/react/fieldset`'s `Fieldset.Root`/
`Legend` (ADR-031): the group semantics and the legend-to-group
`aria-labelledby` wiring both come from Base UI, verified by
`Fieldset.test.tsx` asserting `getByRole("group", { name: ... })` resolves
correctly. `Fieldset.Legend` renders a `<div>`, not a native HTML
`<legend>` — a Base UI upstream choice (its own component, its own
default), not a gap this project introduced or is working around. The
legend text renders through `Text` (`variant="title3"`), the same
reuse-a-token-carrying-nothing pattern `Field` uses for its own label.
`children` is typed as plain `ReactNode`: unlike `Field.Control`, `Fieldset`
has no single element it needs to attach wiring to — it groups an arbitrary
number of controls, so the wider type is correct here rather than a missed
restriction. No wrong turn to record; the component is small enough that the
first draft is the current one.

## SOLID

Single responsibility: the legend-to-group accessible-name wiring and its
token, nothing about which controls the group contains or how many. Liskov
substitution, of a kind: any set of children — checkboxes today, a future
`RadioGroup` tomorrow — sits inside the same `Fieldset.Root`/`Legend`
contract without this component needing to know which. Dependency
inversion: the actual `role="group"` and `aria-labelledby` behavior is Base
UI's; this file owns only the token and the narrower two-prop surface on top
of it.
