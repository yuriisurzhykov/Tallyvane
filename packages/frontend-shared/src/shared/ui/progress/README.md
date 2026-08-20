# progress

A determinate progress bar — Tier 0, per `COMPONENTS.md`'s "Status and
feedback" row, whose one named use case is the weekly application goal
("12 of 20 applications sent this week"). A thin wrapper around
`@base-ui/react/progress`: it knows tokens and a narrowed public surface,
nothing about what is progressing.

## What needed doing

A labelled, accessible bar reading "N of M," where the accessible name, the
`aria-valuenow`/`min`/`max` triple, and the formatted percentage all have to
stay in sync with each other and with the visible fill — real, fiddly
machinery this project already has a policy for reusing rather than
re-solving (`SKILL.md` §6, ADR-031).

## What was actually done

Verified against the installed `@base-ui/react` (1.7.0) source directly —
`ProgressRoot.js`, `ProgressIndicator.js`, `ProgressLabel.js` — rather than
assumed from the `.d.ts` files or upstream docs alone, per this repo's
`verify-before-asserting` rule, because two load-bearing behaviours are not
obvious from the type signatures:

- `Progress.Root` computes `aria-labelledby` itself, from whichever id
  `Progress.Label` registers via context on mount. This component only has
  to render both parts; the association is Base UI's.
- `Progress.Indicator` sets its own `insetInlineStart`/`width`/`height`
  inline, reading the live percentage from context. This component adds
  `absolute` for positioning and `h-inline-tight`/`overflow-hidden` on
  `Track` for the visible bar shape — nothing else, and deliberately no
  `inset-0`-style utility for the indicator's offset, which would resolve to
  nothing under this project's adapter for the same reason `Drawer`'s own
  README documents (the bare `--spacing` multiplier a `0` step would need is
  cleared). Base UI already sets that offset inline, so there was nothing
  left to add.

This component's own public `ProgressProps` is deliberately narrower than
Base UI's `ProgressRootProps`: `value` is `number`, not Base UI's own
`number | null` — indeterminate mode is real in the underlying primitive but
has no call site in `COMPONENTS.md` ("Determinate bar," explicitly), so
exposing it would be surface nobody asked for (YAGNI). `label` is required,
matching `SearchField`'s `clearLabel` and `IconButton`'s `label` — Tier 0
never invents its own copy.

Track thickness (`h-inline-tight`) has no dedicated semantic role — a bar's
thickness is a drawn-line geometry decision, the same category
`timeline-connector.ts`'s own doc comment describes for its connector width,
not a spacing-between-things decision. Rather than adding a new
component-token file for a value this batch's confirmed decisions never
asked this component to own, it borrows an existing spacing-scale utility
for a non-gap dimension — the same precedent `Dot`'s own diameter
(`size-inline`) already set.

## Judgment calls made while building this component

- **No indeterminate mode.** Narrowed away at this component's own type
  level, per YAGNI above — Base UI's `value: null` state still exists one
  layer down if a real call site needs it later.
- **No `tone`/`size` variant.** The confirmed decision fixed this
  explicitly ("no variant surface beyond what's asked").
- **Track thickness as a borrowed spacing-scale class, not a new component
  token.** Considered adding a `progressTokens` component-token file
  (mirroring `statusBadgeTokens`) and rejected it: unlike `Tag` (whose own
  component tokens were an explicit confirmed decision for this batch),
  nothing asked `Progress`/`Meter` to own new theme infrastructure, and
  every additional file under `theme/components/` this batch touches is one
  more surface for a collision with the other groups editing this same
  package concurrently tonight.
- **No width transition on the indicator.** None of the three existing
  transition composites (`hover`/`popover`/`drawer`) target `width`, and the
  one real use case updates rarely (once per logged application), so an
  instant snap was judged not worth a fourth transition composite for this
  batch.

## SOLID

Single responsibility: tokens and a narrowed public surface over Base UI's
own compound API — nothing about what is progressing. Dependency inversion:
all interactive/accessible behaviour is Base UI's; this file only changes if
the visual treatment or the public prop surface does.
