# stack

Tier 0 — vertical flow, with gaps drawn only from the registered spacing
roles (`inline-tight` … `section-gap`). One of the three flow primitives,
alongside `Row` and `Grid`, that exist specifically to make the spacing
scale unavoidable.

## What needed doing

Stacking elements vertically with a gap between them is the single most
common layout need in the product, and every place that needs it faces the
same temptation: reach for an arbitrary `gap-4` or a literal pixel value
instead of asking which of the six registered spacing roles actually
applies. A plain `<div className="flex flex-col gap-...">` at each call
site would mean that temptation wins by default, silently, at every one of
them.

## What was actually done

No Base UI primitive underneath — vertical flex flow has no behavior to
delegate. `gap` is a required prop with no default, for the same reason as
`Grid`'s: naming a role is a decision this component forces rather than
defaults away. The six roles map directly onto the token scale's own
`gap-<role>` utility classes; there is no other way to space `Stack`'s
children apart, which is the entire point of the component existing.
Nothing here needed a second attempt — the whole component is `flex
flex-col` plus a role lookup.

## SOLID

Single responsibility: apply vertical flex flow and one spacing role,
nothing about what fills the stack. Open/closed: a new vertical layout need
is a new composition of `Stack` plus its children, never a new prop on this
component — there is genuinely nothing left to add to a "flex column with a
gap" primitive without it stopping being one.
