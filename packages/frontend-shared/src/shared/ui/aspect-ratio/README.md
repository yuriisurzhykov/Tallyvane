# aspect-ratio

Tier 0 — a media box that reserves its own space before the content inside
it (an image, a video, an embed) has loaded, so the page never shifts once
that content actually arrives.

## What needed doing

Every place this product shows a thumbnail, a screenshot or an embedded
video needs a box whose size is known before the media inside it is, so
layout doesn't jump the moment it loads — a classic cause of layout shift.
`Surface`, `Grid` and `Stack` all know how to lay things out, but none of
them know how to reserve a *ratio*-shaped box ahead of content whose real
dimensions aren't known at render time.

## What was actually done

No Base UI primitive underneath, and not for lack of checking:
`@base-ui/react` 1.7.0's export map has no `aspect-ratio` entry, verified
directly against the installed package rather than assumed from memory.
There's also nothing behavioral to delegate even if one existed — no focus
management, no keyboard path, nothing Base UI's `render`-prop pattern would
be protecting against reimplementing. The CSS `aspect-ratio` property alone
already does the entire job: `ratio` (a plain number, e.g. `16 / 9`) becomes
an inline `style={{ aspectRatio: String(ratio) }}`, wrapped in a `relative
w-full overflow-hidden` box so absolutely-positioned or `fill`-sized media
clips to it. This is a genuinely minimal, self-built substitute for a gap in
the underlying library, not a hand-rolled reimplementation of something Base
UI already solves elsewhere — no wrong turn to record; the CSS property was
correct from the first draft.

## SOLID

Single responsibility: reserve a ratio-shaped box and clip its contents,
nothing about what media goes inside or how it's fetched. Open/closed: a new
ratio is a new numeric argument at the call site, never a new prop or a
branch inside this component — the entire variant surface is the one
`ratio` number, which is already as open as it needs to be.
