# avatar

"One of only two round things in the system" (`COMPONENTS.md`'s "Marks and
identity" row) — Tier 0. `Dot` is the other; where `Dot` carries status,
`Avatar` carries identity, and neither of them knows whose identity or
which status.

## What needed doing

Every entity with a face or a logo — a contact, a company, the signed-in
user in the top bar — needs a small, round image with a graceful fallback
for the extremely common case where there is no image yet, or the URL that
should have one has gone stale. Nothing in `shared/ui` owned "a round image
frame with a fallback" before this component existed, and hand-rolling the
loading-state machinery (has the image finished loading? did it fail? do we
know yet?) at every call site is exactly the kind of headless-primitive
problem `COMPONENTS.md` §2 assigns to Base UI rather than to this package.

## What was actually done

Thin compound wrapper over `@base-ui/react/avatar`: `Root`, `Image`,
`Fallback`. All three loading states — no image provided, still loading, and
a failed load — collapse into the same one Base UI already handles: `Image`
only renders once `imageLoadingStatus === 'loaded'`, and `Fallback` renders
in every other case (verified by reading `AvatarFallback.js`'s own `enabled`
condition, `imageLoadingStatus !== 'loaded'`, directly rather than assumed).
This component adds no branching of its own for "no `src`" versus "`src`
present but broken" — both already land on the same Base UI-owned fallback
path, which is what "graceful fallback for a missing OR failed image" (the
brief's own phrasing) turns out to mean once the actual primitive is read
rather than guessed at.

`Root` owns the one shape decision (`rounded-pill`, the same radius `Dot`
uses for the other round thing) and a `size` prop — `sm`/`md`/`lg` mapped
onto the existing `control` height role's CSS variables, exactly
`IconButton.tsx`'s own `SIZE_CLASS`, reused rather than reinvented. This is
a deliberately different question from `Icon`'s own still-open size scale
(`COMPONENTS.md` §13's proposed 16/20/24, for the glyph *inside* a control):
an avatar is sized like the square/round controls it sits next to in a
toolbar or a table row, which is exactly the scale `control` already names.

## Judgment call: `Fallback`'s content is generic, not a computed-initials prop

The brief asked explicitly for a decision here: initials text as a prop, or
a generic placeholder. Neither, in the form the question implies — `Avatar`
exposes `Fallback` as a plain compound part whose `children` is whatever the
caller wants (initials text, a generic person glyph, anything), the same
way `Toggle`'s and `Menu.Item`'s own `children` are caller-supplied content
this package never inspects. Rejected: a baked-in `initials` prop that
`Avatar` itself computes from a name. Two reasons, both from documents
already in this repo rather than a first guess:

- `COMPONENTS.md` §6 already gives `CompanyMark` ("logo or initials") as a
  named Tier-3 entity component — which means *how* to fall back (initials
  from a name versus a logo image versus something else entirely) is
  already spoken for as domain-level knowledge that lives above this tier,
  not a decision Tier 0 should make on its own.
- "Copy arrives as props below Tier 3" (`COMPONENTS.md` §12) covers a
  Tier-0 component accepting text as a prop; it does not cover a Tier-0
  component *deriving* text (splitting a name, picking the first two
  letters, deciding what happens with a one-word name) — that is a small
  piece of business logic, not a passthrough prop, and belongs in whichever
  Tier-3 entity component actually has a name to derive it from.

`CompanyMark`/`ContactMark` (both already named in `COMPONENTS.md` §6) are
the real call sites that will decide their own fallback content and hand it
to `Avatar.Fallback` as children — this component stays exactly as generic
as `Toggle` or `Menu.Item` already are about their own content.

## SOLID

Single responsibility: the round frame, its size tokens, and exposing Base
UI's already-correct loading-state branching — nothing about whose face or
initials appear inside it. Open/closed: a fourth size is a new entry in
`SIZE_CLASS`, a data change, not a new conditional branch, mirroring
`Dot.tsx`'s own `TONE_CLASS` precedent for the same kind of extension.
Dependency inversion: `imageLoadingStatus`, the `onload`/`onerror` probing,
and the show/hide branching between `Image` and `Fallback` are all Base
UI's; this file owns only tokens and the compound surface Base UI already
ships.
