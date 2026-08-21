# callout

A toned block of explanation — Tier 0, per `COMPONENTS.md`'s "Status and
feedback" row: tax warnings, the LLM budget notice, extraction-confidence
caveats. Always persistent: it explains something about the content it sits
beside, and dismissing it would not make that thing stop being true.

## What needed doing

Something distinguishable from `Toast` (transient, portaled, dismissible —
an event that already happened) and from `Badge` (a short inline label, not
a paragraph) for a block of prose that has to sit in the page's own flow and
keep being true until whatever it describes changes. `COMPONENTS.md`'s own
examples are all several-sentence explanations, not single words, which
rules out reusing either sibling directly rather than building a third
thing.

## What was actually done

A plain `<div role="note">` — no Base UI primitive backs it, the same
"hand-rolled, no real interaction machinery" case `Dot` and `Badge` are.
`role="note"` per the ARIA definition of a section carrying supplementary
information that would not be inappropriate to describe as a footnote —
matching "explains something about the content beside it" more precisely
than a bare `<div>` with no role at all.

Tone resolves to a left-accent border plus a subtle background wash plus a
matching icon colour — one `Record<CalloutTone, string>` map, exhaustive by
construction. Body text is deliberately **not** tone-coloured: it stays
`text-text-primary` regardless of tone, set on an inner wrapper that
overrides the tone colour the outer element applies for the icon's benefit.
A saturated status-text colour reads fine as a short badge word but was not
verified for a multi-sentence paragraph, and the wash plus border already
carry the signal — colouring the prose on top would be double-signalling
for a real legibility risk with no real gain.

The left-accent-plus-wash combination is a deliberate departure from
`ToastRegion.tsx`'s own treatment (border accent only, no wash — see that
file's comment on why it reserved the fuller wash for `Badge`). A callout is
read in the page's own flow rather than glanced at over content behind it,
which is the reasoning for spending the extra visual weight here that a
transient toast does not need.

## Judgment calls made while building this component

- **Full wash *and* left border, not one or the other.** Neither
  `ToastRegion.tsx` nor `COMPONENTS.md` specifies which; `Toast`'s own
  minimal treatment (border only) was considered and rejected above.
- **Body text stays neutral-coloured rather than tone-coloured.** No
  existing sibling settles this either way — `Badge`'s tone-coloured text is
  a single short word, not the same legibility question as a paragraph.
- **`role="note"`**, rather than no role or `role="region"`. The confirmed
  decision fixed everything except the accessible role; `note` is the ARIA
  role whose own definition is the closest match to "supplementary
  explanation attached to nearby content."

## SOLID

Single responsibility: resolving a `tone` to a border/wash/icon-colour triple
and laying out an optional icon beside `children` — nothing about what the
explanation says. Open/closed: a sixth tone is a new map entry. Interface
segregation: no title prop, no dismiss affordance, no action slot —
`COMPONENTS.md`'s examples are icon-plus-prose only, and adding slots no
known call site needs would be the exact YAGNI violation `SKILL.md` §4 warns
against.
