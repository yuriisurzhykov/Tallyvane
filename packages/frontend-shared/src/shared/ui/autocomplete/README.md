# autocomplete

Free text with suggestions, where the value need not be in the list. Tier 0.

## What needed doing

Some fields genuinely are free text — a job title, a search query — but
still benefit from suggestions drawn from history or a known vocabulary,
without forcing the final value to be one of them. `Combobox` (this batch's
own sibling) explicitly does not fit here: Base UI's own usage guidance says
plainly, "Combobox does not allow free-form text input... use Autocomplete
instead."

## What was actually done

`Root`/`InputGroup`/`Input`/`Trigger`/`Clear`/`Popup`/`List`/`Item`/`Group`/
`GroupLabel`/`Separator`/`Empty`, backed entirely by `@base-ui/react/autocomplete`
(ADR-031). No `multiple`-narrowing wrapper is needed here the way
`Combobox.tsx` needs one: verified against `AutocompleteRoot`'s own `.d.ts` —
its two overloaded signatures (grouped items vs. flat) carry no
`Multiple`/selection-mode generic at all, since free-form text has no
"several selected values" concept to guard against.

### Base UI's own package already treats this as "Combobox, minus a few parts"

Verified directly against `autocomplete/index.parts.d.ts` rather than
assumed from the visual similarity: `Icon`, `Clear`, `List`, `Status`,
`Portal`, `Backdrop`, `Positioner`, `Popup`, `Arrow`, `Group`, `GroupLabel`,
`Row`, `Collection`, `Empty` and `Item` are, in the installed package,
literally the identical `Combobox*` components re-exported under an alias —
`AutocompleteItem` is `export const AutocompleteItem = ComboboxItem;`, not a
separate implementation. Only `Root`, `Value`, `Trigger` (itself literally
`ComboboxTrigger` again), `InputGroup` and `Separator` have their own files.
This module mirrors that closeness in its own visual language — matching
token strings with `Combobox.tsx`, not importing from it, the same
duplicate-rather-than-share choice `Menu.tsx`/`Popover.tsx`/`Select.tsx`
already make independently for their own popups (see `Select.tsx`'s own
README for the fuller reasoning).

One deliberate presentation difference from `Select.Item`/`Combobox.Item`:
no reserved indicator column. A suggestion here only ever *fills in* input
text; it does not persist as "the selected value" the way a `Select`/
`Combobox` value does, so there is nothing to mark as selected once the list
closes, and no layout-shift risk to guard against.

### A real, Base-UI-confirmed behavior difference: `ArrowDown` does not pre-highlight

The first draft of the keyboard test assumed `ArrowDown` would open the list
and highlight the first suggestion in one press, mirroring `Combobox`'s own
verified behavior. It does not — the test failed with `data-highlighted`
absent from every option even after `waitFor`. Root-caused by reading
`AriaCombobox.mjs` directly: Floating UI's own `useListNavigation` is
configured with `focusItemOnOpen: queryChangedAfterOpen || (selectionMode
=== 'none' && !autoHighlightMode) ? false : 'auto'`. `Autocomplete` always
runs with `selectionMode: 'none'` (free text has no selection), and
`autoHighlight` defaults to `false`, so `focusItemOnOpen` evaluates to
`false` by default — deliberately: pre-highlighting a suggestion the instant
the list opens would visually suggest a "selected" value the user never
typed or chose, which is exactly the property `COMPONENTS.md`'s own
one-liner for this component calls out ("the value need not be in the
list"). Concretely: the *first* `ArrowDown` only opens the list; the
*second* moves the highlight to the first suggestion. This is a real,
considered behavior difference from `Combobox`, not a gap in this
component's own wrapper — confirmed live (`screen.debug` on the rendered
list after two presses) before writing the corrected test, not assumed from
reading the source alone.

### Same testing gotchas as `Combobox`, applying identically since the parts are literally shared

`Combobox.test.tsx`'s own two findings — the trigger's open is deferred one
animation frame (`useClick(..., { event: 'mousedown' })`), and
`fireEvent.change` does not carry the `inputType` real typing sets, so
`fireEvent.input(..., { inputType: 'insertText' })` is required to make
Base UI treat it as real typing — apply here unchanged, since
`AutocompleteTrigger`/`AutocompleteInput` are, per Base UI's own source,
`ComboboxTrigger`/`ComboboxInput` again.

### A third, newly-found gotcha specific to testing a labeled floating list: the label goes

`aria-hidden` while the popup is open

While writing this component's own tests, a `getByRole("combobox", { name:
"City" })` assertion — one that had already passed once, before the popup
was opened — started failing once re-run *after* the popup opened. The
rendered DOM showed why: Floating UI's own `markOthers` utility (used by any
non-fully-modal floating element to keep background content out of a screen
reader's way while the popup is open, per the WAI-ARIA combobox pattern)
applies `aria-hidden="true"` to everything **outside** the specific
elements Base UI is tracking (the input, the trigger, the popup) — and a
plain native `<label htmlFor>`, correct per Base UI's own forms guide for
this "input outside popup" composition, is not one of those tracked
elements. The *input* itself stays perfectly reachable and functional; only
its *computed accessible name*, which depends entirely on that now-hidden
label per the accessible-name-computation spec, goes blank for as long as
the popup stays open.

This was checked, not left as a guess: swapping the fixture to Base UI's own
`Field.Root`/`Field.Label` (the other option the forms guide names) does
keep the input correctly named while the popup is open — but at a cost of
its own, also verified: `Field.Label`'s `aria-labelledby` context is
broadcast to *every* labelable control inside the same `Field.Root`,
including this component's own icon-only `Trigger`/`Clear` buttons, and
`aria-labelledby` wins over `aria-label` in accessible-name computation
whenever both are present — so the Trigger's own distinct "Show all
cities"-style name gets silently overwritten by the field's own label text
the moment it is wrapped in `Field.Root`. Neither of Base UI's two suggested
labeling strategies is fully clean for a compound field with its own
icon-only sub-buttons, and deciding between "native label, accept the
open-popup name gap" and "`Field.Label`, accept the Trigger/Clear name
collision" is a real product decision, not a mechanical fix — **flagged
explicitly in this batch's own authoring report for morning review**, since
it affects `Combobox`'s and `Select`'s labeling guidance too. This
component's own tests hold a direct element reference captured before the
popup opens, rather than re-querying by accessible name afterward, to stay
correct either way.

## SOLID

Single responsibility: the floating-suggestion mechanics, filtering wiring,
and tokens — nothing about what the free text means (a job title, a search
query). Dependency inversion: the entire state machine — open/close,
filtering, optional inline autocompletion via `mode`, highlight, positioning
— is Base UI's; this file supplies only tokens and the smaller compound
surface on top of it.
