# grid

Tier 0 — column layout with token-driven gaps. One of the three flow
primitives (alongside `Stack` and `Row`) that make the spacing scale the
only way to put air between elements.

## What needed doing

A CSS grid with a chosen number of equal-width columns and a *deliberate*
gap shows up anywhere content needs to line up in columns — card grids,
form layouts, settings panels. Without this component, every call site
would either hand-write `grid-template-columns` and an arbitrary `gap-*`
value, or reach for a raw Tailwind spacing utility that bypasses the
project's spacing-role scale entirely (`gap-4` instead of a role that means
something). `Stack`/`Row` cover one-dimensional flow; nothing already
covered the two-dimensional case.

## What was actually done

No Base UI primitive underneath — there's no interaction to delegate in a
CSS grid layout, only tokens. `gap` is a required prop with **no default**,
a deliberate choice shared with `Stack`/`Row`: forcing a role to be named at
every call site is what keeps a spacing decision from silently defaulting to
whatever role happened to be convenient. `columns` is the one inline style
(`gridTemplateColumns: repeat(${columns}, minmax(0, 1fr))`), because no
token in the system names an arbitrary column count the way `gap` tokens
name a spacing role — the same class of exception as a column *count*,
which is not a CSS length. Drawer width and scrollbar thickness are
tokens now; this `columns` number is still a count. Simple enough that there's no wrong turn here.

## SOLID

Single responsibility: resolve a spacing role to a `gap-*` class and set the
one numeric that has no token, nothing about what the columns contain.
Interface segregation: three props, and `gap`'s lack of a default is itself
part of the interface — a caller cannot accidentally skip the one decision
this component exists to force. No dependency to invert: like `Surface`,
this component's whole contract is a token lookup, not a delegated behavior
that could be swapped later.
