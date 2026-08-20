# truncate

Line clamp with the full value still reachable — Tier 0, and the exact
phrase `COMPONENTS.md` uses for it. It exists so a dense table row (a long
job title, in the canonical case) can clamp at a fixed number of lines
without either breaking the row's height or silently losing the value that
got cut off.

## What needed doing

CSS line-clamping alone solves the overflow problem but not the discovery
problem — once text is clipped, the reader has no way to get the rest of
it back. The obvious fix, wrapping the clamped text in a `Tooltip`, was
considered and explicitly rejected: `Tooltip` doesn't exist yet in this
package, and reaching for a one-off dependency here to solve a single
component's problem is exactly the kind of thing `COMPONENTS.md` warns
against building. The native `title` attribute does the same job — real,
always available, and free of any dependency — so that's what this
component uses instead. This is also why `Truncate` does not compose
`Text` the way `Numeric` does: `Numeric` always wants one fixed typography
variant, but `Truncate` has to wrap children of *any* typography, so fixing
a variant of its own would actively get in the way of its one real job.

## What was actually done

Hand-built, no Base UI dependency — line-clamping is a pure CSS mechanism
Base UI has no opinion about. `lines` drives an inline `WebkitLineClamp`
style, paired with the `display: "-webkit-box"` and `WebkitBoxOrient:
"vertical"` the clamp mechanism actually needs to function. This is an
inline style rather than a token for the same reason `Grid`'s `columns`
prop already is one: no token names an arbitrary line count, so there is
nothing to look up in the theme. The `title` attribute is set only when
`fullValue` is actually passed, via conditional spreading
(`{...(fullValue ? { title: fullValue } : {})}`) rather than assigning
`title={fullValue ?? undefined}` — the pattern
`.cursor/skills/component-authoring/patterns.md` §6 documents for
`exactOptionalPropertyTypes`, so the attribute is genuinely absent, not
present-with-an-empty-value, when there's nothing to show.

One limitation is explicit in the source rather than discovered later: when
`fullValue` is omitted, there is no fallback at all. This component does
not attempt to extract text from `children` to synthesize one, since
`children` may not be a plain string — a caller that wants the full value
reachable has to supply it. There was no wrong turn to correct beyond this
already-documented, intentional limitation; the Tooltip-versus-`title`
choice above was the one real design decision, and it was made once.

## SOLID

Single responsibility: clamping to a line count and, optionally, exposing
the untruncated value — nothing about what the content actually is, which
is why it wraps arbitrary `children` rather than fixing a `Text` variant
the way `Numeric` does. Open/closed: a future switch to a richer fallback
(a real `Tooltip`, once it exists) is a call-site decision — wrap this
component in one — rather than a new prop here; the rejected-dependency
reasoning above is itself that boundary being drawn on purpose. Interface
segregation: `fullValue` is entirely optional and orthogonal to `lines` —
a caller that doesn't need the fallback pays no cost for it, either in
props to supply or in behaviour to reason about.
