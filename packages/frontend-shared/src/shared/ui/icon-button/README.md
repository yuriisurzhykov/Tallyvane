# icon-button

An icon-only clickable action — square at every size, always named. Tier 0.

## What needed doing

A plain `<button>` wrapping a bare icon has no visible tone or size
vocabulary and no accessible name by construction — an icon alone is not a
label. Nothing already in `shared/ui` covered "icon, no text, still a real
button" before this component existed.

## What was actually done

Built via bare `@base-ui/react/use-render` + `mergeProps`, the same pattern
`Text`/`VisuallyHidden` use — at the time this was written, the brief assumed
Base UI had no dedicated `Button` primitive to wrap. That assumption turned
out to be wrong: the `Button` batch (built afterward, in parallel) found Base
UI does ship a real `Button` export with genuine keyboard/disabled-semantics
value over hand-rolled `useRender`, and `Button.tsx` was built on top of it
directly.

This leaves a real, acknowledged inconsistency: `Button` wraps Base UI's real
primitive, `IconButton` does not. Raised explicitly and the answer was to
leave `IconButton` as-is rather than rebuild it — the current implementation
is functionally correct and fully tested; the gap is about which mechanism
supplies keyboard/disabled semantics, not about any observed bug. Revisit
only if a future component needs to compose `IconButton` in a `render`-swap
context where the difference would actually surface.

Tones/sizes mirror `Button`'s own vocabulary exactly (`primary`/`neutral`/
`ghost`/`danger`, `sm`/`md`/`lg`) so the two read as one family, but `size`
here drives both width and height at once — the one respect in which this
differs from `Button`, whose width is always content-driven. `label` is a
required prop, not optional with a fallback: an icon-only control with no
accessible name is not a valid button, so the type system makes omitting it
a compile error rather than a silent a11y gap.

## SOLID

Single responsibility: square-button geometry and the mandatory accessible
name, nothing about what the icon inside it means (the `children` slot is
generic, not typed against any specific icon system, since `Icon`'s own API
is still an open question in `COMPONENTS.md` §13). Liskov substitution holds
against `Button`: both accept the same tone/size vocabulary and the same
`render`-prop polymorphism contract, so a caller reasoning about one already
understands the other.
