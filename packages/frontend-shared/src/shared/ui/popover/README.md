# popover

An anchored panel with collision handling — Tier 0, no domain noun. `Root`/
`Trigger` are Base UI's `@base-ui/react/popover` parts re-exported directly
(they render no tokens of their own); `Popup` is the one part this module
actually earns, bundling Base UI's `Portal` → `Positioner` → `Popup` nesting
behind a single import so a caller never has to rediscover that exact
three-level structure, or remember that this project's overlays must portal
to `document.body` (an ancestor with `backdrop-filter`/`transform`/`filter`
breaks `position: fixed`, per this repo's own methodology notes).

## What needed doing

A generic floating panel, anchored to whatever triggers it, with real
collision-aware positioning — the base every other anchored overlay in this
batch (`Tooltip`, `PreviewCard`, `ContextMenu`) either mirrors or, for
`ContextMenu`, composes from the sibling `Menu` primitive instead.

## A wrong turn worth keeping on record

The first draft put `z-popover` on the `Popup` class, not the `Positioner`.
Rendering it and inspecting the real DOM showed why that is wrong: Base UI's
`Positioner` is the element that actually receives `position: fixed` (via
Floating UI's inline styles); `Popup` itself carries no `position` of its
own. A `z-index` has no effect on a statically positioned element per the
CSS spec, so the first draft's stacking class silently did nothing. Fixed by
moving the class to the `Positioner`'s own `className` — the same fix
`Menu.tsx` (built in parallel, same underlying primitive family) independently
landed on, for the same reason.

A second, related finding: this project's adapter deliberately clears
Tailwind's bare `--spacing` multiplier so an unnamed step (`p-7`) cannot
resolve — but that clearing also drops `size-2`/`size-1.5` on the Arrow's
tip, not just non-zero steps, confirmed by grepping the compiled CSS for the
class and finding it absent. The tip's `width`/`height` are an inline style
now, matching the same numeric value Tailwind's own un-clearable default
would have used, rather than a class that silently compiles to nothing.

## Why this holds up

`Popup` is a styling wrapper, nothing more — every real interaction
(collision-aware repositioning, outside-press and Escape dismissal, focus
return to the trigger) is Base UI's own, verified live in
`Popover.test.tsx` rather than assumed from the prop defaults. `arrow` is an
explicit boolean because there genuinely are two valid visual shapes (with
and without a pointer back at the trigger) and no third; adding a fourth
overlay shape later is a new named part, not a new prop on this one.

## SOLID

Single responsibility: tokens and structure, nothing about what a popover
*contains* — no known content type. Open/closed: a caller extends this by
composing `children`, never by this module growing a new conditional
branch. Dependency inversion: behaviour is entirely delegated to
`@base-ui/react/popover`; this file owns none of it.
