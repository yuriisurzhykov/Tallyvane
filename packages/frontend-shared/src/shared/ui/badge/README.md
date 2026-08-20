# badge

A status label — Tier 0, per `COMPONENTS.md`'s "Status and feedback" row:
`tone` plus a `solid`/`subtle` treatment, backed by the existing
`statusBadge` component tokens. It knows five tones and two treatments and
nothing about what a status *means* — the ten-status-to-tone mapping that
belongs to a real domain (which of the ten application statuses reads as
`danger` versus `attention`) lives in Tier 3's `ApplicationStatusBadge`
(`COMPONENTS.md` §6), not here.

## What needed doing

A compact, pill-shaped label distinguishable from `Dot` (a bare coloured
circle, no text) and from `Button` (a real, pressable action) — something
that reads as "this record's status" while sitting inline in a table cell or
a card header. `statusBadgeTokens` (`theme/components/status-badge.ts`)
already existed before this component did, specified in advance for exactly
this: intrinsically tighter padding than any shared spacing role would want
to represent widely, and a `pill` radius rather than the `chip` step
`Tag`'s own chips use two components over.

## What was actually done

No Base UI primitive backs this — a `<span>`, like `Dot`. Two
`Record<BadgeTone, string>` maps (`SOLID_CLASS`, `SUBTLE_CLASS`) cover the
five tones for each treatment, getting exhaustiveness for free from the
mapped type the same way `Dot`'s own `TONE_CLASS` does, without needing
`Text`'s explicit `switch`/`never` guard.

The token consumption is the one implementation detail worth calling out:
`statusBadgeTokens`' three fields (`paddingX`, `paddingY`, `radius`) are
declared as component tokens precisely because — per that file's own
comment — they would mislead as global spacing/radius roles, which also
means the adapter never gives them a generated bare-utility name the way
`--radius-pill` or `--spacing-inline` get one. This component reads them
through Tailwind's bracket-free custom-property syntax instead —
`rounded-(--ds-component-status-badge-radius)`,
`px-(--ds-component-status-badge-padding-x)` — the exact mechanism
`Button.tsx`'s own `h-(--control-height-md)` already established in this
codebase for a role deliberately kept out of the class-generating
namespaces.

## Judgment calls made while building this component

- **`treatment` defaults to `"subtle"`.** The confirmed decision fixed the
  two treatments but not which one applies with no explicit choice.
  `ToastRegion.tsx`'s own comment already anticipated Badge's `subtle` look
  as the more common case ("a wash, not a fill" — the traffic most badges
  in a dense table or card actually see), so subtle is the default; solid is
  the deliberate, opt-in emphasis.
- **`neutral`'s two treatments borrow from two different existing pairs, not
  a new one.** `subtle` borrows `Dot`'s own neutral fallback
  (`text-secondary` over `surface-inset`, the closest sibling to `text-muted`
  that still reads as a full word rather than a 4px dot). `solid` borrows
  `Button`'s `primary` tone pairing (`interactivePrimary`/`textOnAccent`)
  rather than `textPrimary`/`textOnSolid`: `textPrimary` is near-white in the
  dark theme (`{color.neutral.100}`), which would put white text on a
  near-white fill. `interactivePrimary` is deliberately tuned to always hold
  contrast against its paired `textOnAccent` in both themes, which is
  exactly the property a solid neutral badge needs and no other existing
  role offers without a repeat of the same problem.
- **`statusBadgeTokens.dotSize` stays unused.** It already exists in the
  token file (added ahead of this component, per the file's own history),
  which reads as an invitation to give Badge an inline status dot. Not
  added here: the confirmed decision for this batch is `tone` plus
  `solid`/`subtle` only, no call site in `COMPONENTS.md` yet asks for a
  fused dot-plus-label chip, and `Dot`'s own fixed `size-inline` sizing
  cannot be safely overridden from outside without relying on Tailwind
  class-declaration order (two same-specificity utilities racing) — a real
  fragility, not a hypothetical one. Left as a flagged gap rather than a
  guessed `leadingDot` prop; a real call site should decide the API.

## SOLID

Single responsibility: resolving `tone` and `treatment` to a class pair —
nothing about what a status means, matching `Dot`'s own split between "the
dot" and "which status is which colour" (`ApplicationStatusDot`).
Open/closed: a sixth tone or a third treatment is a new map entry, not a new
conditional branch. Interface segregation: no icon slot, no dismiss
affordance, no size prop — everything `COMPONENTS.md` didn't ask this
component to carry.
