# scroll-area

Tier 0 — a scroll container with a styled track and thumb, so an inner
scroll region never falls back to the browser's bare native scrollbar. Both
axes plus the corner where they meet; one visual treatment, no variant prop,
per the brief this was built against.

## What needed doing

Any list, table or panel that can overflow needs a scrollbar that looks like
part of this design system rather than whatever the OS or browser draws by
default — the same reasoning that gives `Input` real visible styling instead
of inheriting Tailwind's invisible preflight. The actual scrolling mechanics
— overflow detection, drag-to-scroll on the thumb, keyboard support — are
not this component's problem to solve; they already exist, correctly, in
Base UI.

## What was actually done

Wraps Base UI's `ScrollArea` (ADR-031): `Root`/`Viewport`/`Scrollbar`/`Thumb`/
`Corner` all keep their real behavior, and this file supplies only tokens —
`bg-surface-inset` for both scrollbar tracks and the corner, `bg-border-strong`
on the pill-shaped thumbs. `SCROLLBAR_THICKNESS` (`0.5rem`) is a plain
inline-style constant, not a spacing token, because no registered spacing
role names "how thick a scrollbar is" any more than one names "how wide a
drawer is" (`Drawer.tsx`'s own precedent for the same class of exception) —
it's a named identifier rather than a raw literal specifically so it stays a
deliberate, reviewable exemption from `no-raw-dimension-value` rather than
an invisible one. No `variant` prop exists because none was asked for by
this batch's brief; a second visual treatment is a decision for whenever a
real call site needs one, not now.

## The real story: `h-full` needs a real ancestor height, and didn't have one

The first draft of `ScrollArea.stories.tsx` gave the story `className="h-full"`
plus a 45-row list, expecting the tall content to overflow a fixed-height
box and the styled scrollbar to appear. It didn't — the box just grew to fit
all 45 rows instead. `h-full` compiles to `height: 100%`, and a percentage
height only resolves against an ancestor that itself has a real, determinate
height; Storybook's own `html`/`body`/`#storybook-root` chain is auto-height
by default — sized to its content rather than the viewport — so every
ancestor up that chain had the same "grow to fit children" behavior, and
`h-full` had nothing to be a percentage *of*.

The fix, in `packages/storybook/.storybook/preview.css`, is a real
viewport-height anchor, not a workaround at the story level: `html { height:
100dvh }` is the one genuine break-out to the real viewport this chain
needs (`dvh` over `vh` — identical on desktop, correct if this is ever
opened somewhere with dynamic browser chrome), and `body`/`#storybook-root {
height: 100% }` carries that real, resolved size down instead of
re-measuring the viewport at each level. `preview.tsx`'s own decorator
wrapper adds one more `h-full` div on top of that for the same reason, so
the chain reaches all the way from the real viewport down to wherever a
story is actually rendered. Only once every link in that chain resolves to a
real pixel height does the story's own `h-full` finally have a real box to
be 100% of — and only then does Base UI's overflow detection see real,
non-zero `scrollHeight`/`clientHeight` values to compute against, which is
also what lets it size the thumb proportionally to how much of the content
is actually visible, rather than reporting "no overflow" against a box that
simply grew to swallow all of it.

`ScrollArea.test.tsx` cannot exercise any of this: jsdom never computes real
box geometry, so `scrollHeight`/`scrollWidth` are always `0` regardless of
how many rows are rendered, and Base UI's own overflow check
(`clientHeight >= scrollHeight`) reads that as "no overflow" and correctly
unmounts the scrollbar — its documented, correct default, not a bug. The
Vitest suite works around exactly that gap by stubbing a non-zero
`scrollHeight`/`scrollWidth` directly on `HTMLElement.prototype` to simulate
real overflow — a real acknowledgment that jsdom cannot verify the height
chain at all; only a real browser render (Storybook itself, or a future
Playwright suite) can.

## SOLID

Single responsibility: track/thumb/corner tokens, nothing about what's
inside the scrollable region or how tall it should be — that's the caller's
`className`. Dependency inversion: every real behavior — overflow detection,
drag-to-scroll, keyboard support, proportional thumb sizing — is Base UI's;
the one thing this file would need to change if the underlying engine ever
did is the five tokens it currently applies to `Root`/`Scrollbar`/`Thumb`/
`Corner`.
