# spinner

A spinning ring for genuinely slow work — Tier 0, per `COMPONENTS.md`'s
"Status and feedback" row, explicit that this is "only for genuinely slow
work — PDF render, media processing," because ordinary optimistic writes
are meant to show nothing at all rather than a spinner (§12.9's autosave
default). `sm`/`md`/`lg` via the `control` height roles, matching
`Button`/`IconButton`'s own size vocabulary exactly, per this batch's
confirmed decision.

## What needed doing

Two things this component replaces: `Button.tsx`'s own inline
`LoadingIndicator`, a hand-rolled placeholder its own comment already
flagged as temporary ("swap for the real `Spinner` once it exists, the same
'swap this in later' marker `Logo.tsx` uses for its wordmark"); and any
future standalone loading indicator for genuinely slow async work that
is not a button's own busy state.

## What was actually done — and the one real tension in the confirmed decision

The confirmed decision ties Spinner's three sizes to the *same*
`--control-height-sm/md/lg` custom properties `Button`/`IconButton` already
read for their own box height. Taken completely literally — the ring's own
diameter *equal to* the full control height — that breaks the very swap
this batch also asks for: a `size="sm"` `Button` is only 32px tall in
total, and most of that height is already spent on `py-inline` padding
around the text line, so a 32px-diameter ring cannot fit inside a 32px-tall
button next to a label without overflowing it. This was checked against
`Button.tsx`'s own `BASE_CLASS`/`SIZE_CLASS`, not assumed.

The resolution: the ring's diameter reads the *same* `--control-height-*`
custom property (so it is genuinely "via the control height roles," not a
disconnected scale of its own), scaled by a fixed ratio (`/2.5`) rather than
used at 1:1. The ratio was chosen, not guessed at random, so that the "md"
step — `Button`'s own default size — lands on exactly 16px, which is
already this package's established icon size in two other places
(`SearchField.tsx`'s `ICON_SIZE`, `IconButton.stories.tsx`'s
`PlaceholderIcon`). `sm` and `lg` land at 12.8px and 19.2px, proportionate
neighbours of that same anchor.

One consequence worth stating plainly: `Button`'s inline spinner is now
*slightly larger* than it was before this swap (previously a fixed 8px
dot at every button size; now 12.8/16/19.2px depending on the button's own
`size`) and now scales with the button instead of staying constant. This
was treated as a minor, positive side effect of reusing the real component
rather than a regression to avoid — `Button.test.tsx` and
`Button.stories.tsx` were re-verified after the swap and both still
pass/render correctly (see this batch's own report for the full command
output), and nothing in either asserted the old fixed 8px size as a
contract.

Accessibility mirrors `Dot`'s own two-state branch: no `label` means
`aria-hidden="true"` (the spinner conveys nothing on its own — this is
`Button`'s own usage, since the button already carries `aria-busy`); a
`label` means `role="status"` plus the text wired through `VisuallyHidden`
(Tier 0 composing Tier 0), for a spinner used on its own with no other
element already announcing the busy state.

## Judgment calls made while building this component

- **The `/2.5` diameter ratio**, reasoned above from the existing 16px icon
  convention rather than picked freely. This is the most significant
  judgment call in this batch — the confirmed decision did not anticipate
  the literal-diameter conflict with nesting inside `Button`, and a
  different ratio (or a completely different resolution, e.g. two separate
  size vocabularies) was equally plausible without that reasoning chain.
- **No `tone`.** Matches `Icon`'s own still-undecided "colour inherits via
  `currentColor`" reasoning (`COMPONENTS.md` §13) — `border-current` lets it
  sit correctly inside any tone context (a `danger` button's white text, a
  standalone spinner on the page background) without this component making
  its own colour decision.
- **`role="status"` rather than `role="progressbar"` for the labelled
  case.** A spinner has no known percentage (that is exactly what
  distinguishes it from `Progress`), so `status` (a generic live region)
  fits better than `progressbar`, which implies a numeric value this
  component never has.

## SOLID

Single responsibility: a sized, coloured, animated ring — nothing about
what is loading. Open/closed: `Button.tsx`'s swap is the definition of this
principle working as intended — the loading *contract* on `Button`
(disable, `aria-busy`, replace the leading slot) did not change at all;
only the thing rendered inside that slot did.
