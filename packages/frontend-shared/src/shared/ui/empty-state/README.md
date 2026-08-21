# empty-state

Icon, headline, explanation, one action — Tier 1, composing `Stack`/`Text`
(Tier 0). Per `COMPONENTS.md` §4: "every list needs one and they must not
each invent it."

## What needed doing

Every list, table and board in this product eventually renders with zero
rows: no applications yet, no contacts yet, a filtered pipeline with no
matches. Without a shared component, each of those call sites invents its
own version of "nothing here" — a different heading size, a description
that only some of them remember to add, an action button that some of them
centre and some of them don't. The list in `COMPONENTS.md` §4 exists
precisely because this is the kind of drift a single shared component
removes structurally rather than by convention alone.

## Why nothing existing could be reused instead

`Callout` is the closest existing shape (icon slot, message, tone) but it
answers a different question — a toned aside *about* content that is
otherwise present, read inline with the rest of a page (a tax warning, a
budget notice). An empty state *replaces* the content area entirely and
needs its own headline, not a note beside something else. `Panel` supplies
a surface with slots, but its own README is explicit that "a panel with
nothing to show is an `EmptyState`, not an empty `Panel`" — the two are
siblings a caller might combine (an `EmptyState` inside a `Panel`'s body),
not one substituting for the other.

## What was actually done

Four plain props — `icon?`, `title`, `description?`, `action?` — laid out
top to bottom inside a `Stack`, centred by adding `items-center text-center`
to `Stack`'s own `className` passthrough rather than building a second
centred-flex-column primitive beside it. `icon` mirrors `Button`'s and
`Callout`'s own `leadingIcon` convention exactly: a generic `ReactNode`, not
typed against `Icon`'s own still-undecided API (`COMPONENTS.md` §13),
rendered inside an `aria-hidden` wrapper since the headline already carries
the same meaning in words. `title` and `description` are wrapped in `Text`
internally (`title3`/`body`, the latter `color="secondary"`) so a caller
never has to remember to style them — the one thing a raw `ReactNode` prop
could not guarantee. `action` stays an untyped `ReactNode` rather than a
narrower `ButtonProps`-shaped prop, on the same reasoning `COMPONENTS.md`'s
brief for this component states directly: the caller decides what the one
action actually is, and a `Button` is only the common case, not the only
legal one.

No real wrong turn to report: the plain-props shape was evaluated against
`SKILL.md` §3.2's compound-API test first (four stable, non-interacting
slots, no structural variability across call sites) and rejected the
compound API immediately, the same conclusion `Panel`'s own README already
reached for a near-identical shape.

## Why it's understandable, scalable, extensible

Understandable: the prop list reads left to right exactly as the visual
layout renders top to bottom — nothing to infer. Scalable: because every
list in the product uses the same component, a single future change (e.g.
an illustration size convention, once `Icon`'s API lands) updates every
empty list at once. Extensible: a fifth slot, if a real call site ever
needs one, is a new optional prop and a new conditional line — the existing
four never have to change to add it, the same open/closed shape `Panel`'s
own README already establishes for its own three slots.

## SOLID

Single responsibility: laying out four structural slots and centring them,
nothing about what any given empty state actually means in a feature.
Open/closed: a new slot is an additive prop, not a rewrite of the existing
ones. Interface segregation: no prop this component doesn't use — `action`
is untyped `ReactNode` specifically so this component never has to import
`Button`'s own prop shape just to describe a slot it doesn't otherwise
touch. Dependency inversion: depends on `Stack`'s and `Text`'s public
`className`/`variant`/`color` contracts, never on either's internal class
resolution.
