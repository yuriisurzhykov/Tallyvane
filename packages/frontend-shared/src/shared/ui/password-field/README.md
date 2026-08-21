# password-field

Single-line text with a visibility toggle. Tier 0.

## What needed doing

A password field needs a masked/unmasked toggle, and that toggle needs to be
a real, accessible clickable control — not a fourth reimplementation of a
text box just to bolt an icon onto it.

## What was actually done

Composes `Input` and `IconButton` directly, per `COMPONENTS.md` §2's "Tier 0
may compose Tier 0" rule: the toggle is a real `IconButton` (`tone="ghost"`,
matching whatever `size` the field itself uses) absolutely positioned over
`Input`'s own reserved trailing padding, not a separate implementation.
`Input`'s `paddingInlineEnd` is reserved via `calc()` against the same
`control` height role already driving both components' sizing, so the
reserved space always tracks whichever `size` the caller picks rather than
a fixed guess.

The toggle's accessible name is two required props (`showPasswordLabel`/
`hidePasswordLabel`), not one static `label` — because the name should
announce the action the click performs (WAI-ARIA convention for a
visibility toggle: "show password" while hidden, "hide password" while
shown), and `IconButton`'s own `label` prop is static by design. This
component supplies the dynamic swap itself and passes `aria-pressed`
straight through, since `IconButton` exposes no toggle-state mechanism of
its own.

`visible` (masked vs. unmasked) is internal, uncontrolled `useState` —
nothing above this component has any reason to know or drive whether the
password is currently masked, since it's a purely local rendering detail
orthogonal to the field's actual value.

The Eye/EyeOff glyphs use `h-(--control-icon) w-(--control-icon)`, not
Lucide `size={n}`.

## SOLID

Single responsibility: the visibility toggle and its layout over `Input`,
nothing about validation or what the password is for. Composition over
reimplementation: reuses two existing Tier 0 primitives instead of a third,
independent text-box implementation that would need its own separate
disabled/invalid/focus treatment to stay consistent with the other two.
