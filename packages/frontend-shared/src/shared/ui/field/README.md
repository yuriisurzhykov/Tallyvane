# field

Tier 0 — label, description, error and the ARIA wiring between them, wrapped
around a single form control. Every input-shaped Tier 0 component (`Input`,
and eventually `Select`/`NumberField`/etc.) is meant to be used inside one of
these, not bare.

## What needed doing

Associating a label with its control, wiring `aria-describedby` to whichever
of a description or an error is currently showing, and setting
`aria-invalid`/`required` on the control itself is real, fiddly
accessibility machinery that's easy to get subtly wrong by hand — wrong in
a way that passes a casual visual check while still failing a screen reader.
It also needs to happen identically for every field in the product, which is
exactly the "reuse Base UI for anything with real interaction machinery"
case `.cursor/skills/component-authoring/SKILL.md` §6 describes, not a
one-off hand-rolled ARIA pattern.

## What was actually done

A thin styling wrapper over `@base-ui/react/field`'s `Field.Root`/`Label`/
`Control`/`Description`/`Error` (ADR-031): every ARIA relationship —
label-to-control association, `aria-describedby`, `aria-invalid` — comes
from Base UI, verified by `Field.test.tsx` asserting the actual DOM
attributes (`getByLabelText`, `aria-invalid`, `toBeRequired`) rather than
trusting Base UI's documentation alone. This component narrows Base UI's own
surface to five props (`label`, `description`, `error`, `required`,
`children`) and adds one small policy decision: description and error are
mutually exclusive in the rendered output, with error taking priority when
both are passed, since showing a stale hint alongside a live validation
error is more confusing than showing neither. `children` is typed as
`ReactElement`, not the wider `ReactNode`, because it's threaded through
`Field.Control`'s `render` prop, which needs exactly one real element to
attach that ARIA wiring to — a string, fragment or multiple children would
fail Base UI's own contract, so the type says so up front instead of
deferring to a runtime error. Label and error/description text render
through `Text` (`variant="small"`/`"caption"`), reusing its variant and tone
resolution rather than a second, parallel typography decision — composing a
Tier 0 primitive that itself carries no domain knowledge, which
`COMPONENTS.md` §2 draws the line at allowing. No wrong turn recorded here;
the mutual-exclusivity policy and the `ReactElement` typing were both
decided up front, not discovered by a failing test.

## SOLID

Single responsibility: the label/description/error layout and the decision
of which of the two to show, nothing about what kind of control sits inside
it — `Input` today, any future Tier 0 input component tomorrow, without this
file changing. Dependency inversion: every real ARIA behavior belongs to
`@base-ui/react/field`; the one thing that would need to change if the
underlying field engine ever did is this file's five props and the `Text`
variants they render through.
