# multi-select

Several values as removable tags: tech tags, disqualifiers, allowed states,
per `COMPONENTS.md`. Tier 0 — composes `Combobox` (also Tier 0) for most of
its surface, and `@base-ui/react/combobox` directly for the parts `Combobox`
deliberately excludes.

## What needed doing, and why `Combobox` itself can't be it

`combobox/Combobox.tsx`'s own `Root` type is
`Omit<BaseCombobox.Root.Props<Value, false>, "multiple">` — it drops
`multiple` (and its whole type parameter) from the public surface entirely,
by design, and says so in its own comments: enabling `multiple` there would
silently turn on Base UI's own chip-rendering machinery with no chips UI ever
built to show it. `combobox/README.md` names the consequence explicitly:
*this* component — the dedicated multi-value entry point — is a later,
separate batch. This is that batch.

## Verifying the real multi-value API shape, rather than assuming it from `COMPONENTS.md`'s one-line description

Base UI's own `.d.ts` files (read directly, not guessed) confirm a real
`Chips`/`Chip`/`ChipRemove` part family exists:
`combobox/chips/ComboboxChips.d.ts`, `combobox/chip/ComboboxChip.d.ts`,
`combobox/chip-remove/ComboboxChipRemove.d.ts` — plus a `Combobox.Value`
render-prop part (`combobox/value/ComboboxValue.d.ts`) that hands a caller
the current selected-value array with no HTML element of its own. The
official composition (base-ui.com's own multi-select docs and its `d81ec002`
demo source, fetched and read directly rather than inferred) nests
`Combobox.Value` inside `Combobox.Chips`, mapping over the array to render a
`Chip`/`ChipRemove` pair per selected value, followed by the input:

```tsx
<Combobox.InputGroup>
  <Combobox.Chips>
    <Combobox.Value>
      {(value) => value.map((item) => (
        <Combobox.Chip key={item}>
          {item}
          <Combobox.ChipRemove aria-label={`Remove ${item}`} />
        </Combobox.Chip>
      ))}
    </Combobox.Value>
    <Combobox.Input />
  </Combobox.Chips>
</Combobox.InputGroup>
```

`hasSelectionChips` (the selector `ComboboxChips.js` reads to decide whether
to add `role="toolbar"`) is computed purely from `Array.isArray(selectedValue)
&& selectedValue.length > 0` — confirmed by reading `combobox/store.js`
directly — so nothing beyond `multiple: true` on `Root` is needed to make
`Chips`/`Chip`/`ChipRemove` fully live; there is no separate "enable chips"
flag to also set.

## The judgment call this batch's brief explicitly asked for: Base UI's own chip parts, or `Tag`?

`COMPONENTS.md`'s own row says this component "composes `Tag`," and `Tag`'s
own contract (`tone`, `children`, required `onRemove: () => void`, required
`removeLabel`) is exactly shaped for a removable chip. The brief for this
batch asked this to be verified against the installed `.d.ts` and decided
explicitly, rather than assumed either way — so before writing this
component, `ComboboxChip.js` and `ComboboxChipRemove.js` were read directly,
not just their type signatures.

**Reading the implementation surfaced real, non-trivial interaction
machinery `Tag` has no equivalent of:**

- `ComboboxChip` registers itself in a composite list (`useCompositeListItem`)
  and implements roving keyboard navigation between chips: `ArrowLeft`/
  `ArrowRight` (direction-aware, via `getChipNavigationKeys`) moves real DOM
  focus to the adjacent chip; `Backspace`/`Delete` removes the focused chip
  and moves focus to a computed next chip or back to the input;
  `ArrowUp`/`ArrowDown` reopens the popup from a focused chip.
- `ComboboxInput` *itself* reads a `ComboboxChipsContext` to implement a
  second, complementary piece of the same pattern: pressing `Backspace` in
  an *empty* input (with no chip currently highlighted) removes the *last*
  chip, and pressing the leading arrow key with the caret at position `0`
  moves focus onto the last chip — both read directly out of
  `combobox/input/ComboboxInput.js`, not inferred from the docs.
- `ComboboxChips` sets `role="toolbar"` automatically once any chip exists,
  specifically because "NVDA enters browse mode instead of staying in focus
  mode when navigating with arrow keys inside a container unless it has a
  toolbar role" — a real, specific screen-reader accommodation quoted
  verbatim from the source's own comment.

`Tag` is a plain function component with a *fixed* prop signature —
`{ tone, children, onRemove, removeLabel, className }`, no `...rest` spread,
no `render` prop, no forwarded ref. `BaseCombobox.Chip`'s own `render`
mechanism needs to inject `ref`, `tabIndex`, and a real `onKeyDown` handler
onto whatever element it wraps to make the machinery above work at all. Even
if `<BaseCombobox.Chip render={<Tag .../>} />` were attempted, `Tag`'s
destructuring would silently **drop** every one of those injected props —
they would never reach a real DOM node, since `Tag` never spreads unknown
props onto its own root `<span>`. This was verified against `Tag.tsx`'s own
source directly (its function signature has no rest parameter at all), not
merely suspected.

**Decision: this component uses Base UI's own `Chip`/`ChipRemove`, styled to
look exactly like `Tag`** (`Chip`/`ChipRemove` duplicate `Tag.tsx`'s own
`BASE_CLASS`/`TONE_CLASS`/`REMOVE_BUTTON_CLASS` strings verbatim, plus one
deliberate addition — `focus-visible:focus-ring` on `Chip` itself, since
unlike `Tag`'s own outer `<span>`, `BaseCombobox.Chip` is a genuinely
focusable element during arrow-key roving navigation). This keeps every
behavior above — none of it is reproducible without either hand-rolling the
same ARIA combobox-with-chips pattern from scratch (exactly the volume/
frequency threshold `SKILL.md` §6/`patterns.md` §7 says not to cross at this
project's scale) or accepting a visibly worse, keyboard-incomplete chip row.
`TagTone` (the *type*, not the component) is still reused for the tone
vocabulary, keeping that one piece genuinely DRY.

## Everything else: reused directly from

`Combobox`, not duplicated — a deliberate departure from an established precedent

`combobox/README.md` documents why `Select.Item`/`Combobox.Item`/`Menu.Item`
share matching class *strings* but stay separate implementations: "backed by
different Base UI primitives with different props." That reasoning does not
hold here. `MultiSelect`'s `Input`/`Trigger`/`Clear`/`Popup`/`List`/`Item`/
`Group`/`GroupLabel`/`Separator`/`Empty` are backed by the **exact same**
`@base-ui/react/combobox` primitives `Combobox.tsx` already wraps — same
import, same component, same props, verified by reading every one of the
relevant `.d.ts` files directly and confirming none of them carry a `Value`/
`Multiple` type parameter that would make them behave differently under a
`multiple: true` root. Duplicating ~150 lines of behavior-sensitive Base UI
wrapper code for zero behavioral difference would be the opposite of DRY,
and would let the two components silently drift the next time one file is
edited and the other forgotten — worse than the risk the Select/Combobox
precedent is actually guarding against. So this component imports
`Combobox`'s own exported parts directly (`Combobox.Input`, `Combobox.Popup`,
etc.) — Tier 0 composing Tier 0, the same allowance `COMPONENTS.md` §2
already gives `NumberField.Increment`/`Decrement` composing `IconButton`.

**`InputGroup` is the one exception, and a real one, not a copy left
unduplicated by accident.** `Combobox.InputGroup` has a *fixed* height,
correct for a single-line text field; a chips field's box has to *grow* as
chips wrap onto a second or third row, which a fixed height cannot express.
This module's own `InputGroup` uses `min-h-*` plus real vertical padding
instead — documented in `MultiSelect.tsx`'s own comment, and demonstrated
growing in `MultiSelect.stories.tsx`.

## Selected items still show a checkmark in the popup list — for free

`Combobox.Item`'s own `ItemIndicator` reads Base UI's own `isSelected`
selector, which already branches on `Array.isArray(selectedValue)` for
multi-select (`combobox/store.js`, read directly: `.some(...)` for arrays,
direct comparison otherwise). Reusing `Combobox.Item` unmodified means
already-selected items are marked in the list automatically, with no
special-casing needed in this file at all.

## Real, empirically-verified behavior, not assumed

`MultiSelect.test.tsx` includes a real interaction test that selects an item
from the popup, then selects the **same** item again, and asserts what
actually happens — proven live rather than guessed from a doc that does not
say — alongside real add/remove-tag tests (selecting from the list, clicking
a chip's `ChipRemove`, and `Backspace` in an empty input removing the last
chip). It confirmed, live: clicking an already-selected item in the list
**toggles it off** (the standard checkbox-list convention for a multi-select
combobox), not a no-op and not an error.

## Two real testing gotchas, found only by running the interaction tests

1. **Chips are `aria-hidden` for as long as the popup is open — the exact
   mechanism `lessons-learned.mdc` already documents for a plain sibling
   `<label>`, now confirmed to reach further than just the label.** Floating
   UI's `markOthers` (the standard WAI-ARIA combobox pattern's "hide
   irrelevant background content while expanded") applies `aria-hidden="true"`
   to everything outside the tracked reference/floating pair — not just the
   label, but the *entire* surrounding subtree, chips included. A first draft
   of the "select two items" test asserted `getByRole("button", { name:
   "Remove React" })` immediately after clicking a second item in the still-
   open popup, and failed with "unable to find an accessible element" even
   though `render()`'s own debug output showed the button sitting right there
   in the DOM, `aria-label` and all — `aria-hidden="true"` on an ancestor
   `<div>` is what hid it from the query, confirmed by inspecting the actual
   rendered attributes rather than assumed from the error message alone.
   Fixed by closing the popup (`Escape`, which closes without clearing the
   selection while the popup is mounted — the same safe pattern
   `combobox/Combobox.test.tsx`'s own "closes on Escape" test already uses)
   before asserting on any chip by role.
2. **The combobox input's own *computed accessible name* goes blank for the
   same reason, for the same duration.** `lessons-learned.mdc` already
   documents this for `Combobox`/`Autocomplete`'s plain sibling `<label>`;
   this batch re-hit it independently while fixing gotcha 1 above, since
   re-querying `getByRole("combobox", { name: "Tech tags" })` *after* opening
   the popup failed outright — the label text itself is exactly what the
   `markOthers` sweep hides, and the accessible name computation reads it.
   Fixed the same way the existing lesson prescribes: hold a direct element
   reference captured *before* the popup opens, and reuse that reference
   rather than re-querying by name afterward.

## SOLID

Single responsibility: the multi-value shape (chips, the array boundary,
`multiple: true`) and nothing about what any one selected value *means* — a
tech tag, a disqualifier and an allowed state are all just `MultiSelect`
underneath. Open/closed: the floating-list look is extended by `Combobox`'s
own evolution automatically, since this component imports its parts rather
than copying them — a future visual change to `Combobox.Item` reaches this
component with no edit here at all. Dependency inversion: every real
interaction concern (open/close, filtering, roving chip focus, Backspace/
arrow-key chip navigation, dismissal) is Base UI's own; this file supplies
only tokens, the `multiple` restriction, and the six parts that genuinely
differ once chips are real.
