# visually-hidden

Screen-reader-only content — Tier 0, and per `COMPONENTS.md`, "the partner
of every icon-only control." Present in the accessibility tree, removed
from the visual layout by clipping rather than by hiding — the distinction
that makes this component worth having at all.

## What needed doing

Every icon-only control (`IconButton`, and any future one) needs an
accessible name that sighted users don't need to see, and getting that
technique right matters more than it looks: `display: none` or
`visibility: hidden` would hide the text from assistive technology too, not
just from the page's visual layout, silently defeating the entire purpose.
Nothing else in this tier already provides that clipping technique, and
leaving each caller to reach for `sr-only` correctly on its own is exactly
the kind of accessibility machinery `.cursor/skills/component-authoring/SKILL.md`
§6 says to centralize rather than re-risk at every call site. `Dot` already
depends on this component for its own optional accessible name — the
first real consumer, and the case `COMPONENTS.md` §2 cites by name as a
Tier-0-composing-Tier-0 primitive carrying no domain knowledge across the
boundary.

## What was actually done

No Base UI primitive corresponds to "visually hidden text" — this is a
pure CSS technique, implemented with Tailwind's own core `sr-only` utility.
That utility is notable in this codebase specifically because it reads from
no theme scale, so it stays available even though this package's own
`@theme` block deliberately clears every bare Tailwind spacing/colour
namespace tokens are normally named from (the same adapter behaviour
`Drawer.tsx`'s README documents catching a real bug in). Polymorphism comes
from Base UI's `useRender`/`mergeProps`, the same pair `Text` uses — needed
because the hidden text sometimes has to be a real `<label>` (for a `Field`
association) rather than a plain `<span>`, shown directly by the `AsLabel`
story and its matching test.

There was no wrong turn here to correct. The four tests cover exactly the
properties that matter — renders as a span by default with the `sr-only`
class, stays out of `display: none`/`visibility: hidden` (the actual
correctness property, not just "has a class"), swaps element via `render`,
and merges a caller `className` without dropping its own — and nothing
about the implementation needed a second pass.

## SOLID

Single responsibility: exactly one technique, clip visually while staying
in the accessibility tree — nothing about what the hidden text says or why
it's needed, which varies entirely by caller (an icon's label, a field's
label, a skip link's destination). Open/closed: the `render` prop lets a
caller target a `<label>` or any other element `useRender` accepts without
a boolean `as="label"` prop or a second, near-identical component.
Dependency inversion: this component depends on Base UI's `useRender`
abstraction for polymorphism rather than a hand-rolled children-cloning
mechanism, the same abstraction `Text` depends on — a future change to how
this project does polymorphism touches both consistently instead of only
one.
