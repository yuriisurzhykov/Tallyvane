# checkbox-group

Coordinates several `Checkbox`es sharing one array of ticked values — Tier 0.

## What needed doing

A tag filter, a "which channels can this rule notify on" list, or a "select
all" parent checkbox all need several checkboxes to agree on one shared
array of ticked values without each one tracking its siblings by hand — the
same coordination problem `ToggleGroup` solves for `Toggle`, applied to
`Checkbox`.

## What was actually done

Thin styling wrapper over `@base-ui/react/checkbox-group`. All the actual
coordination — the shared ticked-values array, and the parent/child
"select all" wiring `useCheckboxGroupParent` provides internally — is Base
UI's; this component supplies only the list layout (`flex flex-col`, per
below) and the inter-item gap token. A child `Checkbox` rendered inside
this group reads its own checked state from the group's shared context
automatically, the same way `Toggle` does inside `ToggleGroup` — this
wrapper passes nothing down to it itself.

Vertical by default, unlike `ToggleGroup`'s horizontal row: a checkbox
group is conventionally a list of options read top-to-bottom (a tag filter,
a settings list), while `ToggleGroup` coordinates an inline set of
view-switcher buttons. No `orientation` prop of its own — Base UI's
`CheckboxGroup.d.ts` has none to delegate to (unlike `ToggleGroup`'s own
prop, confirmed by reading both declaration files side by side), and no
known call site needs a horizontal checkbox list yet. `className` stays
open for a caller that genuinely does, per the layout-and-position-only
passthrough rule.

No real wrong turn: the shape matched `ToggleGroup`'s own precedent from
the start.

## SOLID

Single responsibility: list layout and gap tokens for a group of
checkboxes, nothing about selection logic itself. Interface segregation: a
caller composing `Checkbox`es inside this group depends only on
`value`/`defaultValue`/`onValueChange`/`allValues`, never on how Base UI
implements the parent/child indeterminate computation internally.
