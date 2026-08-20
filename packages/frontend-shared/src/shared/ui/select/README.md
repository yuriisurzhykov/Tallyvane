# select

Choice from a known short list: work mode, seniority, filing status. Tier 0.

## What needed doing

Any field whose value comes from a short, closed set of options (a handful
of work modes, seniority levels, filing statuses) needs a real, accessible
dropdown — one that behaves like a native `<select>` for keyboard and screen
reader users, without this product hand-rolling the ARIA listbox pattern for
every one of those fields separately.

## What was actually done

`Root`/`Trigger`/`Value`/`Icon`/`Popup`/`Item`/`Group`/`GroupLabel`/
`Separator`/`Label`, backed entirely by `@base-ui/react/select` (ADR-031):
open/close, roving highlight, keyboard typeahead, `Home`/`End`/arrow
navigation, and the special "overlap the trigger so the selected item's text
lines up with the trigger's own value text" positioning are all Base UI's.
`Root` is a bare re-export — it renders no DOM at all — the same reasoning
`Menu.Root`/`Popover.Root` already established for the identical shape.
`modal` is left at Base UI's own `true` default: a short, known list being
actively chosen from reads correctly as a brief, page-scroll-locking
interaction, the same intent behind a native `<select>`'s own platform popup
— unlike `Popover.tsx`'s own non-modal default, which is deliberately for a
different kind of overlay ("anchored to a trigger, dismissed by clicking
away," per the `z-popover` role's own stated intent).

### How much visual language is shared with `Menu`, and why

`Select`'s popup and item classes intentionally *look* like `Menu`'s
(`z-popover` placement, `rounded-card`/`shadow-elevation2` popup,
`data-[highlighted]:bg-surface-row-hover` items) — both are floating,
keyboard-navigable lists, and a product where a dropdown menu and a select
popup looked different for no reason would read as inconsistent. They do
not, however, literally share a component or a module: `Menu`'s own parts
are action-oriented (`Item.leadingIcon`, `Item.shortcut`) and carry no
`selected`/`ItemIndicator`/`ItemText` concept, which `Select.Item` genuinely
needs. Each file duplicates the matching token strings instead of importing
from the other, the same choice `Menu.tsx` and `Popover.tsx` already made
independently for their own, separately-defined but visually identical
popup styling — not a new pattern invented here.

One real, deliberate divergence from `Menu.Item`: `Select.Item` uses
`cursor-default`, not `Menu.Item`'s `cursor-pointer`. Verified against Base
UI's own reference demo, which does the same — a menu item reads as a
button-like action, while a select item is a row in a list of values (a
native `<select>`'s own `<option>` shows no pointer cursor either).

`Select.Item`'s check indicator sits in a fixed-width leading column that
stays reserved whether or not the current item is selected. `ItemIndicator`
itself unmounts when unselected; reserving its column regardless avoids
every other item's text shifting sideways as selection changes — a detail a
snapshot test would never catch, since it only shows one selection state at
a time.

`Select.Separator` and `Menu.Separator` are, per Base UI's own source,
different things: `Menu.Separator` literally re-exports the generic
`@base-ui/react/separator`, while `Select.Separator` is its own small file.
Read directly rather than assumed identical — its documented surface
(`orientation`/`className`/`style`/`render`) turned out to be exactly the
same as the generic separator's, with no select-specific state, so this
component reuses this package's own `Separator` directly here too, the same
choice `Menu.tsx` already made for the same reason.

## A real bug, found only by actually running the interaction test

The first draft of `Select.test.tsx` selected an option with a bare
`fireEvent.click(option)`, mirroring `Menu.test.tsx`'s own click helper. It
silently did nothing — the option never became selected and the popup never
closed. Root-caused by reading `SelectItem.mjs` directly: Base UI's own
`onClick` handler refuses a mouse selection unless a preceding `onPointerDown`
already set an internal `allowMouseSelectionRef` to `true`, which is exactly
what every real browser mouse interaction produces (`pointerdown` always
precedes `click`) but a bare synthetic `click` does not. Fixed by dispatching
`pointerdown` immediately before `click` in the test's own selection helper.
This is the same class of gap `Menu.test.tsx`'s own `detail: 0` vs
`detail: 1` finding already documents — a jsdom `fireEvent.click` alone is
not automatically a faithful simulation of a real mouse click for this
family of Base UI component, and needs verifying per component rather than
assumed to carry over.

A second, related finding while writing the `Enter`-to-select test: dispatching
`keyDown` for `Enter` on the `listbox` container (as `Menu.test.tsx` does for
its own `Escape`/arrow-key tests) left the list open — Base UI attaches its
`Enter` activation to each option's own real, roving `tabindex`, not to a
list-level handler, so the event has to be dispatched on the currently
highlighted option itself to reach it. Recorded here, and in the workspace
lesson catalog, since it applies to `Combobox`'s and `Autocomplete`'s own
tests in this same batch.

## Judgment call: this package's own `Field` does not fit `Select`

Same finding as `number-field/README.md`'s own section on this: Base UI's
forms guide places `Select.Root` as a direct child of a bare `Field.Root`,
labelled via `Select.Label` (not `Field.Control`). This package's own
`field/Field.tsx` forces its child through `Field.Control`, a shape built
for a single simple element — it does not fit `Select` either, for the same
reason it does not fit `NumberField`. See that README for the full
reasoning; not repeated here to keep one home for the fact.

## SOLID

Single responsibility: the floating-list mechanics and its tokens, nothing
about what any given option means — `WorkModeBadge`'s mapping from a work
mode value to a tone lives in `entities/job`, not here. Dependency
inversion: the entire state machine — open/close, highlight, positioning,
typeahead — is Base UI's; this file supplies only tokens and the smaller
compound surface on top of it.
