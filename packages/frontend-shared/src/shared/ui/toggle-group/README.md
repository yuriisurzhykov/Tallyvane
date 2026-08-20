# toggle-group

Coordinates a row (or column) of `Toggle`s into a single exclusive-choice or
multi-select control. Tier 0.

## What needed doing

A group of `Toggle`s needs shared roving-focus and arrow-key navigation, and
needs to agree on which one (or ones) are pressed without each `Toggle`
tracking its neighbors by hand — the same coordination problem radio-button
groups solve for native inputs, applied to this design system's own
`Toggle`.

## What was actually done

Thin styling wrapper over `@base-ui/react/toggle-group`. All the actual
coordination — exclusive-vs-multi selection, roving `tabIndex`, arrow-key
movement — is Base UI's; this component supplies only the flex layout (row
by default, column when `orientation="vertical"`, both driven by Base UI's
own `data-orientation` attribute) and the inter-item gap token. `Toggle`
children read their pressed state from the group's own shared context
automatically — this wrapper does not pass anything down to them itself.
`value`/`defaultValue`/`onValueChange` (an array of pressed values) and
`multiple` (default `false`, exclusive choice) are Base UI's own
controlled/uncontrolled vocabulary, verified against the installed
`ToggleGroup.d.ts` rather than assumed.

No real wrong turn: the shape matched the plan from the start.

## SOLID

Single responsibility: layout and gap tokens for a group of toggles, nothing
about selection logic itself. Interface segregation: a caller composing
`Toggle`s inside this group depends only on `value`/`onValueChange`/
`multiple`, never on how Base UI implements roving focus internally.
