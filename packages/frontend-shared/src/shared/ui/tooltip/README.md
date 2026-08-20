# tooltip

A hint for sighted users, never the only carrier of information — Tier 0.
Lighter than `Popover`'s panel (`shadow-elevation1`, `text-caption`) because
a tooltip is the least visually weighted overlay in the system: one rung
below an anchored panel that can hold real controls, and `z-tooltip` is the
topmost named layer for exactly that reason — it must be summonable from a
control on any other layer, including a toast.

## What needed doing

The generic "hover/focus reveals a hint" primitive, built on
`@base-ui/react/tooltip`. `Provider` is included (not just `Root`/`Trigger`/
`Popup`) because it exists upstream specifically to let several tooltips in
one list (a table's action column) share one open delay instead of each
paying the full delay independently — a real, near-certain need in this
product given how many icon-only controls it has.

## Two things discovered only by running it, not by reading the types

Base UI's own hover-open path for a trigger with **no** `Provider` routes
through Floating UI's "rest" mechanism, not a plain hover-then-wait:
`onMouseEnter` is a deliberate no-op whenever a rest-only delay is
configured, and the actual open timer only starts once the pointer goes
*still* over the trigger, tracked via `onMouseMove`. A real mouse fires
`mousemove` continuously while hovering; a test firing only `mouseenter`
never opens it — not a bug in the component, but a real behavioural detail
`Tooltip.test.tsx`'s own comment documents so the next person does not
have to rediscover it by trial and error.

Keyboard-focus opening is gated on `:focus-visible` (`useFocus.js`, shared
with `PreviewCard`) — correct real-browser behaviour (a mouse-click focus
should not summon a tooltip; a Tab-key focus should), but jsdom hardcodes
`Element.matches(":focus-visible")` to always return `false` regardless of
input modality, confirmed directly by trying both a plain `.focus()` and a
`keydown`-then-`.focus()` sequence. That specific path is untestable under
Vitest and is flagged, not silently skipped — it would need a real browser.

`z-tooltip` and the Arrow tip's size live on the Positioner and as an inline
style respectively, for the same reasons `Popover.tsx`'s own README records.

## SOLID

Single responsibility: presence and positioning of a hint, nothing about
its content. No `Close` part — unlike `Popover`, a tooltip holds no
interactive content of its own, so there is nothing to dismiss beyond
hover-out, blur, or Escape, all of which are Base UI's.
