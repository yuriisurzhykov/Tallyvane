# numeric

Tabular-figure typography, right-aligned by default — Tier 0, and per
`COMPONENTS.md`, "every salary, count and date in a column." It is a thin
composition on top of `Text`, not a second typography implementation.

## What needed doing

Every numeric column in this product — salaries, counts, dates — needs the
same two things every time: `Text`'s `numeric` variant, and right alignment
so the figures actually line up as a column instead of ragging left. Doing
that at each call site (`<Text variant="numeric" className="text-right">`,
repeated at every salary cell, every count, every date) is exactly the kind
of repetition that earns a real component rather than a documentation
note, since it's two decisions bundled together across many call sites, not
one prop renamed once. `variant="numeric"` is deliberately not exposed as
a prop here — a caller who wants a different `Text` variant while calling
this component "numeric" would defeat the entire point of it existing.

This component does not format numbers, and that boundary is deliberate,
not an oversight: turning an amount, a date, or a count into a display
string is domain logic, and belongs to a Tier 1+ component (`Money`,
`DateTime`) that knows what the number actually represents. `Numeric` only
supplies the right typography and alignment for whatever string the caller
has already formatted — reusing `Numeric` from inside `Money`/`DateTime`
once they exist is the intended shape, not a gap to fill here.

## What was actually done

Composes `Text` directly rather than reimplementing typography — the
`COMPONENTS.md` §2 exception for a Tier 0 primitive composing another Tier
0 primitive applies cleanly here, since `Text` itself still knows nothing
but tokens and `Numeric` is the one deciding which variant to fix. No Base
UI dependency, since there is no interactive behaviour involved. `align`
is a two-value `"left" | "right"` prop defaulting to `"right"`, backed by a
plain `Record` (`ALIGN_CLASS`) rather than a `switch` — there's no third
alignment value to make exhaustiveness checking worth the extra ceremony
`Text`'s ten-variant switch needed. There was no wrong turn to correct
here; this is a small, deliberate wrapper that does exactly the two things
its own doc comment says it does.

## SOLID

Single responsibility: fixing typography variant and default alignment for
numeric content — nothing about how a number, date, or amount gets turned
into the string it renders, which stays a Tier 1+ concern. Open/closed: a
third alignment value would be a new entry in `ALIGN_CLASS`, not new
branching logic. Dependency inversion in the composition sense: `Numeric`
depends on `Text`'s public `variant`/`className` contract, not on `Text`'s
internal `VARIANT_CLASS` resolution — a token rename inside `Text` doesn't
require touching this file at all.
