# dot

The status dot: a small filled circle carrying a `tone` — Tier 0, and per
`COMPONENTS.md`'s "Marks and identity" row, "the other round thing," besides
`Avatar`. Where `Avatar` carries identity, `Dot` carries status, and nothing
else in this package renders a bare coloured circle.

## What needed doing

A compact, non-textual status indicator that fits inline next to a label —
a sidebar count, a table row, a list item — without pulling in `Badge`'s
full text-plus-fill-plus-border treatment. `tone` has no default: "a dot
with no meaning is pointless," so every call site is forced to state what
the dot means rather than falling back to some ambient neutral colour that
would just as easily have meant nothing was wrong. The actual status
vocabulary — which of the ten application statuses maps to which tone — is
explicitly not this component's job; that mapping lives in Tier 3
(`ApplicationStatusDot`, per `COMPONENTS.md` §6), keeping `Dot` itself free
of any domain noun.

## What was actually done

No Base UI primitive backs this — it is a `<span>` with a background colour
and a fixed `size-inline`/`rounded-pill` shape, composing `VisuallyHidden`
for its optional accessible name rather than reimplementing that clipping
technique a second time (the exact "Tier 0 composing Tier 0, no domain
knowledge crossing the boundary" case `COMPONENTS.md` §2 calls out
explicitly for this component). Five tones — `neutral`/`info`/`attention`/
`success`/`danger` — sit in a `Record<DotTone, string>`, which gets
exhaustiveness for free from the mapped type over `DotTone`, without needing
Text's explicit `switch`/`never` guard.

The one deliberate, documented choice: backgrounds come from each tone's
*text* colour role (`bg-status-info-text`, etc.), not its fill role. A fill
role is deep enough to carry white text on top of it, which makes it nearly
invisible as a small dot on a dark page — so `Dot` borrows the same role
`Text`'s own tone resolution already uses, the same way
`.cursor/skills/component-authoring/patterns.md`'s `toneToRole` example
borrows `text-muted` for a tone union with no dedicated "neutral" colour of
its own.

Accessibility is a plain two-state branch, not a variant: no `label` means
`aria-hidden="true"` (the dot conveys no meaning on its own, verified by a
dedicated test); a `label` means no `aria-hidden` and the text is wired
through `VisuallyHidden` rather than a native `title` or `aria-label`
attribute, so it reads the same way `Truncate`'s and `KeyboardKey`'s own
accessible-content patterns do elsewhere in this tier. There is no wrong
turn to report here — the tone-role choice above was the one real design
decision, and it was made once, correctly, with a comment explaining why.

## SOLID

Single responsibility: resolving a `tone` to a background colour and an
optional accessible name — nothing about what a status *means*, which is
exactly why the ten-status-to-tone mapping was pushed up to
`ApplicationStatusDot` instead of living here. Open/closed: a sixth tone is
a new entry in `TONE_CLASS`, a data change, not a new conditional branch.
Dependency inversion in the small sense that matters here: `Dot` depends on
`VisuallyHidden`'s public contract (children in, screen-reader-only content
out), not on how it achieves that — the clipping technique could change
without `Dot` ever needing to.
