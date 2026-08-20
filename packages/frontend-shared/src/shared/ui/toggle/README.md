# toggle

A two-state pressed/unpressed button. Tier 0.

## What needed doing

Density switches, table-versus-board views, and theme pickers (per
`COMPONENTS.md`'s row for this pair) all need the same "pressed" visual
language and the same keyboard-activatable two-state behavior — a plain
`<button>` with manual `aria-pressed` bookkeeping would mean every call site
re-implementing the same state machine and the same a11y wiring by hand.

## What was actually done

Thin styling wrapper over `@base-ui/react/toggle`. Pressed state, keyboard
activation, and disabled semantics are entirely Base UI's (ADR-031): this
component reads the `data-pressed`/`data-disabled` attributes Base UI itself
emits and maps them onto tokens (`interactive-primary-subtle` background,
`interactive-primary-text` text when pressed), rather than tracking any
state of its own. `pressed`/`defaultPressed`/`onPressedChange` are Base UI's
own controlled/uncontrolled trio, verified against the installed package's
own `.d.ts` rather than assumed — this component adds no second state
mechanism on top.

No real wrong turn here: the implementation matched the plan from the start,
since Base UI's `Toggle` primitive already covers exactly the behavior this
component needs.

## SOLID

Single responsibility: pressed-state tokens only. Dependency inversion: the
actual state machine (controlled vs. uncontrolled, keyboard activation) is
Base UI's; this file would need no change if that underlying mechanism were
ever swapped for a different one, since it only reads the data attributes
the contract already promises.
