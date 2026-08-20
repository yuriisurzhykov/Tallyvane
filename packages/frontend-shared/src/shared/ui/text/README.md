# text

Renders one of the ten text styles on a polymorphic element — Tier 0, and
per its own row in `COMPONENTS.md`, "the only way type is applied." Every
other typographic primitive in this package (`Numeric`, and any future
`Money`/`DateTime`) is a composition on top of this one, not a second way to
reach the same ten styles.

## What needed doing

Nothing in the DOM or in Base UI gives a "render one of our ten design-token
text styles" primitive — Base UI ships behavior, not typography. Without
this component, every call site would either hardcode the variant class
names directly (a hole in the token discipline, the same class of problem
`COMPONENTS.md` §11 already calls out for colour) or invent its own
one-off styling. Centralizing the variant → class and tone/color → class
mappings in `VARIANT_CLASS`/`COLOR_CLASS`/`TONE_CLASS` means a token rename
touches this one file, not every screen that renders text.

## What was actually done

No Base UI component backs this — there is nothing to reuse for "pick a
text style," only the `useRender`/`mergeProps` utilities `ADR-031` already
designates as this project's polymorphism mechanism, the same pair
`VisuallyHidden` uses. The ten variants (`display`, `title1`–`title3`,
`body`, `bodyStrong`, `small`, `caption`, `overline`, `numeric`) are fixed
in a `Record<TextVariant, string>`, and the default-tag switch ends in
`default: { const exhaustive: never = variant; ... }` — an eleventh variant
without a case is a compile error, not a silent fallthrough.

Colour is a discriminated union, not two independent optional props:
`tone?: "neutral"` leaves `color` free to pick among `primary`/`secondary`/
`muted`, while any real status tone (`info`/`attention`/`success`/`danger`)
sets `color?: never`. A status tone already implies its own text colour, so
allowing both to be set would leave one of them silently ignored — the test
suite asserts this is a compile-time-only rejection (`@ts-expect-error`),
not a runtime guard.

The default-tag choice is the one place this component makes a real,
deliberate call rather than an obvious one: heading variants (`display`,
`title1`–`title3`) default to `<span>`, not a real heading tag. axe's
heading-order and one-`<h1>`-per-page rules assume a document outline, and
this component has no way to know whether a given usage is the page's one
true heading or the fifteenth card title in a list — reusing the variant
tag anywhere else on the page would silently produce a broken outline the
moment a real heading element became the default. A caller that knows a
usage genuinely is a heading opts in explicitly via `render={<h1 />}` (through
`h6`), verified by both the "regression guard" test (`title1` stays a
`SPAN`) and the `AsPageHeading` story. `body`/`bodyStrong` default to a real
`<p>` instead, since paragraphs carry no such uniqueness constraint. There
was no wrong turn to correct here — this is the shipped design, not a fix.

## SOLID

Single responsibility: resolving variant/tone/color into a class name and
picking a sensible default tag — nothing about what the text says, which
arrives as `children` from the caller. Open/closed: the `render` prop lets
a caller swap the emitted element (a real `<h1>`, a `<label>`, whatever
`useRender` accepts) without this component's body ever branching on
caller intent; the only closed part is the ten-variant switch itself, and
that closure is deliberate — an eleventh variant is meant to be a compile
error. Interface segregation: the tone/color discriminated union means a
caller asking for a status tone is never even offered the `color` prop at
the type level, rather than being allowed to set it and having it silently
ignored at runtime.
