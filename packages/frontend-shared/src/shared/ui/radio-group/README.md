# radio-group

Coordinates several `Radio`s into one mutually-exclusive choice — Tier 0.

## What needed doing

Work mode, seniority, filing status — every enumerated single-choice field
in `COMPONENTS.md`'s "Inputs" table needs several radios to agree on
exactly one selected value, with arrow-key navigation moving both focus and
selection between them, the same coordination problem `ToggleGroup` solves
for `Toggle` and `CheckboxGroup` solves for `Checkbox`.

## What was actually done

Thin styling wrapper over `@base-ui/react/radio-group`. Verified, not
assumed, by reading `RadioGroup.js` directly: the shared selected-value
state and the arrow-key roving-tabindex navigation between radios both
come from Base UI's own `CompositeRoot` internals — genuine JS keyboard
handling, not reliance on native browser same-`name` radio grouping (which
would not be exercisable the same way in a jsdom test at all). This
component supplies only the list layout and gap token.

One real, empirically-found detail belongs on record: after an arrow key,
Base UI moves DOM focus to the newly active radio from inside a layout
effect that runs after the keydown handler's own state update commits, not
synchronously inside the handler. A first draft of `RadioGroup.test.tsx`
asserted `toHaveFocus()` immediately after `fireEvent.keyDown` and failed
even though the roving `tabindex` had already moved to the right element —
the fix was `await waitFor(() => expect(...).toHaveFocus())`, not a change
to this component. Left in the test file's own comment, per this
project's own methodology on recording a wrong turn rather than quietly
fixing it.

Vertical by default, same reasoning as `CheckboxGroup`'s own — see that
file's README.

## SOLID

Single responsibility: list layout and gap tokens, nothing about selection
logic. Interface segregation: a caller composing `Radio`s inside this
group depends only on `value`/`defaultValue`/`onValueChange`, never on how
Base UI implements roving focus internally.
