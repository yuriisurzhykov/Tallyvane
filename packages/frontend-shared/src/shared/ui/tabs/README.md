# tabs

`COMPONENTS.md`'s "Disclosure" row for this component ships with no Purpose
text at all yet — this batch's authoring report supplies the finalized
one-liner. Tier 0: it switches which one of several panels is visible; it
knows nothing about what a pipeline, a board, or any other panel actually
is.

## What needed doing

Several pieces of content need to occupy the same screen space, one at a
time, switched by a row of labelled controls — the pipeline's table-versus-
board toggle, and any settings screen with more sections than fit
comfortably as separate scroll regions. This batch's own brief fixed the
visual direction already (see the next section) rather than leaving it
open, since `ToggleGroup` already exists in this package and the two
controls are easy to confuse without a stated reason for looking different.

## The confirmed decision, and how it was actually built

The brief fixed the shape: segmented/pill, visually related to
`ToggleGroup`, not an underline indicator — and named the real tool for it
directly (Base UI's `Tabs.Indicator`) rather than leaving "hand-roll a
per-tab active background" open as an option. `Tabs.Indicator` does exist
(confirmed against `tabs/index.parts.d.ts` before writing anything), and it
is a genuinely different mechanism from colouring each active `Tab`
directly: Base UI computes one shared element's `left`/`top`/`width`/
`height` from whichever tab is currently active (via CSS custom properties
— verified by reading `TabsIndicator.js`), so exactly one filled pill exists
at a time and can slide between tabs, rather than each tab owning its own
background that would have to snap on and off with no shared element to
animate between two positions.

`data-[active]:text-interactive-primary-text` on `Tab` and
`bg-interactive-primary-subtle` on `Indicator` together are literally
`Toggle`'s own `data-[pressed]:text-interactive-primary-text`/
`data-[pressed]:bg-interactive-primary-subtle` pair, split across two
elements instead of one: `Toggle` puts both on the same button because it
has no shared sibling to draw a background on; `Tabs` has `Indicator`, so
the fill moves there while the text-colour half stays exactly where
`Toggle`'s own precedent already puts it. This is what "visually related to
`ToggleGroup`'s look" means literally, not just in spirit.

## Judgment call: the border sits on `List`, not on each `Tab`

`Toggle`'s own border is per-item, since each `Toggle` is a fully
independent control. A `Tab` inside `Tabs` is not — exactly one is ever
active, and the sliding `Indicator` already draws the "this one is
selected" signal, so giving every `Tab` its own border as well would either
fight the indicator visually or duplicate the boundary it already draws.
Instead, `List` itself gets `border border-border-default rounded-control
p-inline-tight` — one frame around the whole segmented control, matching
the shape a macOS-style segmented control or ToggleGroup-adjacent design
actually reads as, with the fill living only on the one active spot inside
it.

## Judgment call: no animation on the sliding indicator

Base UI's own `Indicator` is built to be animated — the CSS variables update
on every selection change specifically so a caller can transition `left`/
`top`/`width`/`height` smoothly. This component leaves it a hard cut instead.
None of this project's three transition composites (`hover`, `popover`,
`drawer`, per `composites/transitions.ts`) include those properties, and
that omission is itself deliberate per that file's own comment ("`transition:
all` is never what anyone means... it makes the browser watch every property
on the element for a change"). Adding a fourth, narrower composite for this
one case is the right long-term answer, but it means editing a shared theme
file while three other groups are working in this same package tonight —
the same real-conflict-risk-for-a-cosmetic-change reasoning
`collapsible/README.md` gives for its own, unrelated height-transition gap.
Revisit both gaps together once this batch and the other three land.

## Judgment call: a rotating/animated custom indicator uses `render`, not a hand-rolled one

Not applicable in quite the same way `Accordion.Trigger`'s indicator
judgment call is (`Tabs.Indicator` already exists and is used directly
here), but the same underlying tool is available if a caller ever needs a
`Tab`-level decoration that reacts to `state.active`: the function-form
`render` prop already exposes it, the same way `Accordion.tsx`'s own
`Trigger` documents for `state.open`.

## What was actually done, mechanically

Five parts — `Root`, `List`, `Tab`, `Indicator`, `Panel` — matching Base
UI's own five real parts, the same "don't collapse structurally independent
parts" reasoning `Accordion.tsx`'s own five-part shape uses one directory
over. `orientation` (default `"horizontal"`) is Base UI's own prop, passed
straight through; `Root` reads it too (`data-[orientation=vertical]:flex-
row`), not just `List`, because a vertical tab list needs its panel beside
it, not stacked underneath — `List`'s own orientation-aware layout on its
own would have produced a vertical list of tabs sitting above a panel that
still reads left-to-right underneath it, which is not what "vertical tabs"
means.

`multiple` has no equivalent here — exactly one tab is ever active, which
is the entire distinction between `Tabs` (mutually exclusive, one panel
visible) and `Accordion` (`multiple` optionally allows several open at
once).

## A finding this batch's own test coverage caught, not assumed

The brief asked explicitly for keyboard behaviour to be verified
empirically rather than assumed, and this component is where that mattered
twice:

- **Manual, not automatic, activation is the real default.** `TabsList`'s
  own `activateOnFocus` defaults to `false` (confirmed against
  `TabsList.d.ts`), meaning arrow keys move roving focus between tabs
  without selecting them — `Enter`/`Space` (or focus itself, if a caller
  opts into `activateOnFocus`) is what actually switches the visible panel.
  `Tabs.test.tsx`'s own keyboard suite asserts this split directly (arrow
  key moves focus and leaves `aria-selected` unchanged; a subsequent click —
  standing in for the real Enter/Space activation jsdom cannot simulate on a
  native `<button>`, the identical limitation `Menu.test.tsx` already
  documents — is what flips it).
- **A disabled tab stays reachable by arrow keys, unreachable to
  activate** — matching `Menu`'s own disabled-item precedent, not a
  difference between the two components. The first draft of this suite
  seemed to show the opposite (focus refusing to move onto a disabled tab
  at all), which turned out to be a test bug, not a real behavioural
  difference: calling `.focus()` directly on a tab that was never selected
  does not reliably resynchronize Base UI's internal roving-focus index on
  its own, so the keydown handler was computing from a stale index. Clicking
  the tab first — a real selection, which is also the more realistic user
  path anyway — fixed the test and confirmed the disabled tab genuinely is
  reachable, exactly matching `Menu`. Recorded here because it is exactly
  the kind of wrong turn this repo's own methodology says to keep, not
  quietly erase once the right answer was found.

## SOLID

Single responsibility: which one of several panels is visible and the
shared tokens for the control that switches it, nothing about what any
panel contains. Open/closed: a fourth tab is new `Tab`/`Panel` JSX at the
call site, never a new prop or branch here. Liskov substitution: every
`Tab` behaves identically regardless of how many siblings it has —
`Indicator` reacts to whichever one is active without any tab needing to
know it exists. Dependency inversion: selection state, roving focus,
manual-activation semantics, and the indicator's own position math are all
Base UI's; this file owns only tokens and the two deliberate omissions
(indicator border-on-`List` placement, no geometry transition) explained
above.
