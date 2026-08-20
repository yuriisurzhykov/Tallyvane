# row

Tier 0 — horizontal flow, gaps only from the registered spacing roles, and
centred on the cross axis by default. The horizontal counterpart to `Stack`.

## What needed doing

The same spacing-scale problem `Stack` solves for vertical layout applies
horizontally — an icon next to a label, a button next to another button —
and the horizontal case has one extra wrinkle `Stack` doesn't: without an
explicit cross-axis alignment, two items of different heights sitting side
by side don't share a common middle line, which looks wrong for the
icon-plus-label pairing that's the actual common case. Nothing existing
solved horizontal flow with that alignment already decided.

## What was actually done

No Base UI primitive underneath, same reasoning as `Stack` — horizontal flex
flow has no behavior to delegate. The one real decision beyond mirroring
`Stack`'s required, default-less `gap` is `items-center` applied
unconditionally: `Row` centres its children on the cross axis by default,
because the common case (icon beside label, button beside button) wants a
shared middle line, not each child sitting at its own top edge. No wrong
turn — this was the intended default from the first draft, not a correction
of a broken one.

## SOLID

Single responsibility: horizontal flex flow, cross-axis centring, and one
spacing role — nothing about what fills the row. Interface segregation:
identical prop surface to `Stack`'s, which is deliberate rather than
duplicated by accident — the two components solve the same problem on two
different axes, and a caller switching between them shouldn't have to learn
a second API to do it.
