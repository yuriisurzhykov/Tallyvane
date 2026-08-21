# skeleton

A loading placeholder — Tier 0, per `COMPONENTS.md`'s "Status and feedback"
row, with one hard constraint stated in the row itself: must stay still
under `prefers-reduced-motion`. It knows nothing about what it is standing
in for — a text line, an avatar, a card — only how to occupy that shape
while pulsing gently.

## What needed doing

A generic placeholder box any caller can size to match whatever real content
has not arrived yet, that pulses to signal "this is loading" without
becoming a second source of motion-sickness risk for a reader who has asked
the system to minimise motion. `COMPONENTS.md` §12 already states reduced
motion is handled globally by the adapter — the work here was verifying that
claim against this component's own animation, not reimplementing a local
media query.

## What was actually done

A `<div>` with no intrinsic size: nothing about "a skeleton" implies a
particular width or height, since it always mimics something else's shape.
Two defaults exist purely so `<Skeleton />` with no props is visible at all
rather than collapsing to nothing — `h-stack` (one spacing-scale step,
~16px, approximating a single line of body text) and `w-full` (meaningful
specifically inside a flex/grid row, where a childless block with no
explicit width collapses to zero) — both fully overridable via `className`
or `style`.

The animation is a `<style>` tag holding its own `@keyframes`, the identical
technique `Button.tsx`'s `LoadingIndicator` already uses and for the same
reason: the adapter clears `--animate-*` to `initial`
(`theme/adapters/tailwind.css`), so a theme-keyed `animate-pulse` utility
would silently resolve to nothing rather than fail loudly.

**Verifying the reduced-motion requirement** meant reading
`theme/adapters/tailwind.css`'s existing global rule rather than assuming
"handled globally" was still true or adding a redundant local one: it forces
`animation-duration: 1ms !important` and `animation-iteration-count: 1
!important` on every element, including this one, via `*, *::before,
*::after`. An `!important` author-stylesheet rule overrides a plain inline
`style` declaration for the same property regardless of specificity, so this
holds even though the animation itself is set inline rather than through a
class — confirmed by reading the CSS cascade rule that makes it true, not
assumed from the class/inline distinction looking safe. The practical effect
under reduced motion: the pulse plays once, for one millisecond, and lands
on its final keyframe (`opacity: 1`) — visually still, not merely slower.

This is a CSS-cascade guarantee, not something a jsdom-based Vitest test can
observe directly: jsdom does not evaluate `@media` blocks from an actual
stylesheet against `getComputedStyle`, so `Skeleton.test.tsx` verifies what
is actually under this component's control (a real `animation` property,
its own declared `@keyframes`, no theme-keyed utility class) and leaves the
end-to-end reduced-motion claim to the project's real-Chromium accessibility
suites, consistent with how `Drawer`'s own real-browser bug was only ever
going to be caught the same way.

## Judgment calls made while building this component

- **`h-stack`/`w-full` as the no-props default**, rather than shipping with
  no visible size at all. No role names "a line of placeholder content";
  `h-stack` borrows an existing spacing-scale step for a non-gap geometry
  value, the same precedent `Dot`'s own diameter (`size-inline`) already
  set in this codebase.
- **`aria-hidden="true"` unconditionally, with no prop to change it.** A
  skeleton is decorative by construction; the "loading" announcement itself
  is judged to belong to whatever composes several of these into a real
  loading state (`LoadingRegion`, Tier 1, not built yet), which is the
  layer that actually knows it is showing a loading state rather than five
  independent decorative boxes.

## SOLID

Single responsibility: occupying a shape and pulsing — nothing about what
loads into it. Open/closed: a new shape is a `className` at the call site,
never a new prop or conditional branch here. Dependency inversion in the
small sense that matters: this component depends on the adapter's global
reduced-motion rule being true, not on reimplementing it, so a future retune
of that rule (e.g. a different reduced-motion duration) changes nothing
here.
