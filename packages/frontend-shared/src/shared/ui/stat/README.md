# stat

One large number with a label and an optional delta — Tier 1, composing
`Row`/`Stack`/`Text` (Tier 0). Per `COMPONENTS.md` §4: "analytics, and the
Today header."

## What needed doing

Analytics and the Today header both need the same small shape repeated many
times over: a short label above (or beside) a number, sometimes with a
change indicator next to it — "Applications this week, 12, +12% vs last
week." Left to each call site, the label/value/delta arrangement, the
spacing between them, and the colour a delta takes for "up" versus "down"
versus "no change" would each get re-decided per screen, which is exactly
the drift a shared Tier 1 component exists to prevent.

## Why nothing existing could be reused instead

`Numeric` and `Money` already solve *what the number looks like*, but
neither one lays out a label above it or a delta beside it — composing
either of them inside a bare `<div>` at every call site would leave the
label/value/delta arrangement unsolved and re-invented per screen, the
exact problem this component exists to remove. `KeyValueList` is the
nearest sibling shape (a label and a value) but it is built for many rows
of *paired* facts read top to bottom, not one large emphasized number with
a directional delta — forcing `Stat` through `KeyValueList` would mean
disabling most of what makes a key-value row a key-value row (many items,
uniform typography) to get back to a single emphasized one.

## What was actually done

`label`, `value` and an optional `delta` are the entire public surface.
`value` is deliberately `ReactNode`, not a `number` this component formats
itself — `COMPONENTS.md`'s own brief for this component states the caller
passes an already-built `Numeric` or `Money`, and `Stat`'s job is only
where the label and delta sit around whatever the caller already built.
This mirrors `Panel`'s own `header`/`footer` shape: a structural slot for
content the caller decides, not a re-decision of that content's own
typography.

**Judgment call: `delta.tone` forwards straight to `Text`'s own tone
resolution, not a second colour lookup table.** The temptation was a
`Record<StatDeltaTone, string>` mirroring `Badge`'s/`Callout`'s own
`TONE_CLASS` maps. Rejected: `COMPONENTS.md` §2 states explicitly that
`Text`'s own `variant`/`tone` resolution "counts as neither [domain nor
presentation knowledge]," since the composing component still chooses
which tone to pass — reusing that resolution here is the same DRY move
`Numeric` already makes for typography, applied to colour instead. The one
piece of real logic left is `DeltaText`, a four-line internal helper that
spells out the `"neutral"` case explicitly (`Text`'s own tone union leaves
`"neutral"` to a separate `color` prop, so `Stat`'s `StatDeltaTone` — a
narrower three-value union of `"success" | "danger" | "neutral"`, since a
delta only ever moves one of three ways — cannot be forwarded as a single
prop without that branch).

No real wrong turn to report: the "forward to `Text`, don't reinvent a
tone map" choice was the first candidate and held up under `COMPONENTS.md`
§2's own test on first read.

## Why it's understandable, scalable, extensible

Understandable: three props, and the visual order (label, then value and
delta together) matches the prop declaration order. Scalable: because
`value` accepts any `ReactNode`, every existing and future Tier 0/1
formatter (`Numeric`, `Money`, a future `DateTime`) plugs into the same
`Stat` without this component ever needing to know about a new one.
Extensible: a fourth slot — a trend sparkline, say — is a new optional prop
beside `delta`, not a restructuring of the two that already exist.

## SOLID

Single responsibility: label/value/delta layout, nothing about how the
value or delta strings were produced. Open/closed: a new delta tone would
be a `never`-checked exhaustiveness gap in `DeltaText`'s own two-branch
conditional (currently a plain ternary since there are only two branches
to route — `Text` itself owns the actual five-tone `switch`, per its own
`resolveColorClassName`). Dependency inversion: `Stat` depends on `Text`'s
public `tone`/`color` contract, never on `Text`'s internal `TONE_CLASS`/
`COLOR_CLASS` lookup tables.
