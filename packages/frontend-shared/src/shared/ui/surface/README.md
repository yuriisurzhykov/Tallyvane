# surface

Tier 0 — a themed background with a border and no shadow: the card shape
that sits *in* the page flow, as opposed to something that floats above it.
`Panel`, `Toast`, `PreviewCard` and everything Tier 3+ that needs a card
surface are meant to compose this rather than re-deciding what a card's
background, border and radius should be.

## What needed doing

Every "card" in this product needs the same three tokens — a background
role, `border-subtle`, and `rounded-card` — applied the same way regardless
of which of the three surface levels (`primary`/`elevated`/`inset`) it's
showing. Nothing else in `shared/ui` owned this: `Stack`/`Row`/`Grid` own
layout, not background, and per `composites/shadows.ts`, elevation (shadow)
is reserved for things that float above content they didn't lay out —
overlays, not a surface sitting in normal flow. Without this component,
every card-shaped call site would either duplicate the same three classes or
quietly reach for a shadow that belongs to a different visual language.

## What was actually done

No Base UI primitive underneath — there's no behavior here to delegate (no
focus management, no keyboard path, no ARIA relationship), just a
variant-to-token lookup. `variant` selects one of three background roles
(`bg-surface-primary`/`elevated`/`inset`); the border and radius are applied
unconditionally, regardless of variant, because "surface" always means the
same border and radius — only the fill changes. `className` is accepted for
layout and position only, per `COMPONENTS.md` §11. Straightforward enough
that there's no wrong turn to record: the whole component is one `Record`
lookup and a `.filter(Boolean).join(" ")`.

## SOLID

Single responsibility: resolve a variant to its background token and apply
the two invariant style facts (border, radius) — nothing about what fills
the surface or where it sits on the page. Interface segregation: three
props, none unused for any caller — `variant` defaults to the common case so
a plain `<Surface>` is already a valid call. There's no dependency to invert
here the way there is for `Separator` or `ScrollArea`: the abstraction this
component protects call sites from is the token *names* themselves, not a
behavioral engine that could be swapped out later.
