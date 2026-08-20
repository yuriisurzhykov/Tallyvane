# preview-card

Hover preview for a linked job or contact — Tier 0. Unlike `Popover.Trigger`/
`Tooltip.Trigger`, Base UI's own `PreviewCard.Trigger` always renders a real
`<a>` (verified against its own `.d.ts`: no `render`-swap escape hatch is
documented for a non-anchor case) — it exists specifically to preview
*linked* content, matching this component's stated purpose exactly. A
caller always supplies a real `href`; there is no variant that previews
something un-navigable.

## What needed doing

The same anchored-panel shape as `Popover`, but weighted like one
(`shadow-elevation2`, `rounded-card`) rather than like `Tooltip`: a preview
carries real content — a job's title, company, status — not a one-line
hint, so it reads as a small `Surface`, the same call `Popover.Popup`
already makes for the same reason.

## Two things this component shares with `Tooltip`, verified rather than assumed to be identical

The default (no-`Provider`) open delay here is **not** routed through the
"rest" mechanism `Tooltip`'s own trigger uses — `PreviewCardTrigger.js`'s
`delay()` callback returns a real `open` value, so a plain `mouseenter` is
enough to start the timer; no `mousemove` needed. Confirmed by writing the
test both ways and finding the simpler one already correct — the two
Base UI triggers only *look* like the same hover pattern from the outside.

Keyboard-focus opening, on the other hand, genuinely is identical to
`Tooltip`'s: both use `useFocus.js`'s `:focus-visible` gate, and jsdom
hardcodes that pseudo-class to always fail regardless of input modality —
the same verified, reported (not silently skipped) gap `Tooltip.test.tsx`
documents.

`z-popover` and the Arrow tip's inline-style size follow `Popover.tsx`'s own
README for the reasoning; not re-derived independently here since it is the
same underlying Floating UI positioning mechanism either way.

## SOLID

Single responsibility: positioning and chrome for a hover preview, nothing
about what a "job" or "contact" is — the caller supplies that as `children`.
No `Close` part, same reasoning as `Tooltip`: dismissed by moving away, not
by an action inside it.
