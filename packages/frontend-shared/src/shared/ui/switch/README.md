# switch

An on/off setting, applied immediately — Tier 0. Per-rule reminder
enablement (`COMPONENTS.md`'s own row for this component) is the first
known call site: a setting that takes effect the moment it's flipped,
unlike a checkbox inside a form that waits for submission.

## What needed doing

Same visibility problem `Checkbox`'s and `Radio`'s own READMEs document —
Base UI's `Switch.Root` is a bare, unstyled `<span>` — plus one more: a
switch is expected to visibly *slide*, not just recolour, when it changes
state.

## What was actually done

Thin styling wrapper over `@base-ui/react/switch`'s `Switch.Root` +
`Switch.Thumb` (ADR-031): checked state, keyboard activation and disabled
semantics are entirely Base UI's, verified against the installed
`SwitchRoot.d.ts`.

Track and thumb sizes are `--ds-component-switch-*` tokens (track
2.5rem × 1.5rem, thumb and travel 1rem). A named rem constant used to
be the lint exemption; that hole is closed. The numbers still add up
against the registered spacing roles used for the rest of the track:
track height minus twice the inset padding
(`p-inline-tight`, an actual registered token used here for its literal
padding meaning, not borrowed the way `Dot`'s `size-inline` borrows a
spacing role for a diameter) leaves exactly the thumb size, and the
remaining horizontal room is exactly the travel token.

The thumb slides via `transform: translateX(...)` rather than a
`justify-start`/`justify-end` flip on the track — a real, considered
choice, not the first idea reached for. `justify-content` is a discrete
CSS property with no defined interpolation, so a browser snaps it at the
transition's halfway point instead of animating smoothly; the sliding
motion a switch is expected to have would be lost entirely. The travel
distance is a CSS custom property (`--switch-thumb-travel`) set inline on
the thumb and read back through Tailwind's `translate-x-(--switch-thumb-travel)`
paren syntax — the exact mechanism `Input`/`Button`/`IconButton` already
use for `h-(--control-height-*)` — so no bare pixel value ever appears in
a class name, and `Switch.test.tsx` asserts the actual custom-property
value rather than trusting the class name alone.

`transition-transform` is Tailwind's own bare, tokenless utility, not a
themed `transition-*` role: it only turns on `transition-property: transform`
at Tailwind's stock default duration/easing, since neither is cleared by
the adapter the way `--ease-*`/`--animate-*` are. A themed alternative
would need a new utility in `theme/adapters/tailwind.css`, out of scope for
a single component — flagged in this batch's authoring report as a
judgment call. The global `prefers-reduced-motion` rule in that same file
still collapses this transition regardless, since it targets `*`
unconditionally.

## SOLID

Single responsibility: checked-state tokens and the slide affordance,
nothing about what the setting controls. Dependency inversion: the actual
state machine is Base UI's; this file only reads the data attributes the
contract promises and supplies the geometry Base UI's unstyled primitive
does not opinionate on.
