# collapsible

The third depth level's open/close mechanics — Tier 0. `COMPONENTS.md`'s own
line for this component is "row expansion is this component inside a table
row," which is also the constraint on everything below: it knows how to open
and close, and nothing about what it is opening onto.

## What needed doing

A single disclosure — one trigger, one panel, nothing coordinated with a
sibling — shows up in at least two different shapes: an icon-only expand
caret in a narrow table cell, and a full-width labelled header in a taller
content area. Nothing in `shared/ui` covered either shape before this
component existed, and `Accordion` (built alongside this one) is the wrong
tool for a single, uncoordinated disclosure — reaching for a whole
accordion group to open one row would drag in `multiple`/`value` semantics
that a single row has no use for.

## What was actually done

Thin compound wrapper over `@base-ui/react/collapsible`: `Root`, `Trigger`,
`Panel`. `Root` is re-exported directly with no wrapping `<div>` of its own
— it renders a real `<div>` (confirmed against `CollapsibleRoot.d.ts`'s own
doc comment, unlike `Menu.Root`, which renders no DOM at all), but that div
needs no visual treatment: `Trigger` then `Panel` already stack correctly in
plain block flow. A wrapper that only renames a prop doesn't earn its place
(`SKILL.md` §7), so `Root` skips the ceremony `Menu.Root`/`Menu.Trigger`
already established for the same situation.

`Trigger` and `Panel` both got wrapped, since both carry real tokens
(padding, hover, focus-ring on the trigger; `overflow-hidden` on the panel).
Neither bakes in a layout opinion beyond that — no `w-full`/`justify-between`
on `Trigger`, no padding or typography on `Panel` — because the two known
shapes above want opposite defaults: a table row's expand caret wants
content-width and its own padding rules; a FAQ-style header wants the label
pushed to one edge and an indicator pushed to the other, with `p-stack`
inside the panel. Baking in either shape would have made the other one fight
the defaults instead of just adding a class. `multiple`/`value` do not
exist here at all — `Accordion`, not this component, is what coordinates
more than one panel.

## The judgment call this batch could not fully resolve: no height transition

Base UI's own `CollapsiblePanel` already exposes exactly what a slide
animation needs — a `--collapsible-panel-height` CSS variable and
`data-starting-style`/`data-ending-style` attributes (verified by reading
`CollapsiblePanel.js` directly) — so wiring up an animated open/close would
have been a CSS-only addition, not a Base UI limitation. It was left out
anyway: doing it right needs `transition-property: height`, and this
project's three transition composites (`composites/transitions.ts`) are
`hover` (color/background/border/opacity), `popover` (opacity/transform) and
`drawer` (transform) — none include `height`, and that file's own comment
explains why: "`transition: all` is never what anyone means... it makes the
browser watch every property on the element for a change." Adding a fourth,
narrower composite for this one case would be the correct long-term answer,
but it means editing a shared theme file while three other groups are
working in this same package tonight — real conflict risk for a purely
cosmetic addition. The panel opens and closes instantly instead. Revisit by
adding a `transitionHeight`-shaped composite once this batch and the other
three land, at which point `Panel`'s own `className` gains one more token
and nothing about its public API changes.

## SOLID

Single responsibility: the trigger/panel open-close relationship and its
tokens, nothing about what fills the panel — every real call site (a table
row, a FAQ block, a settings section) supplies its own content as children.
Open/closed: a new disclosure shape is a new `className` at the call site,
never a new prop here. Dependency inversion: `aria-expanded`/`aria-controls`
wiring, the `data-panel-open`/`data-open`/`data-closed` state attributes,
and Enter/Space activation on the trigger's real `<button>` are all Base
UI's; this file owns only tokens and the one deliberately-deferred animation
decision above.
