# combobox

Choice from a long or remote list: company, contact, tag. Tier 0.

## What needed doing

`Select` (this batch's own sibling component) is right for a handful of
known options, but is not filterable beyond keyboard typeahead — Base UI's
own usage guidance for `Select` says so explicitly: "prefer Combobox... when
the number of items is sufficiently large to warrant filtering." Company,
contact and tag pickers are exactly that case, and some of them (company,
contact) are backed by a remote search rather than a small local array.

## What was actually done

`Root`/`InputGroup`/`Input`/`Trigger`/`Clear`/`Popup`/`List`/`Item`/`Group`/
`GroupLabel`/`Separator`/`Empty`, backed entirely by `@base-ui/react/combobox`
(ADR-031). Filtering itself needs no extra work from this component either
way: for a real local list, Base UI's own default `contains` match runs
against the `items` prop with zero configuration; for a remote, server-searched
list, the caller supplies `filteredItems` (or drives `useFilter`) directly —
both are already-shipped Base UI features, not something layered on here.

### The one real narrowing of Base UI's own API: no `multiple`

`Combobox.Root`'s own generic signature is `<Value, Multiple>`, and Base UI
would happily flip on chip-rendering machinery (`Chips`/`Chip`/`ChipRemove`)
the moment `multiple` is `true`. This module drops both the `multiple` prop
and its `Multiple` type parameter entirely: `MultiSelect` (`COMPONENTS.md`'s
own row — "several values as removable tags," composing `Tag` and this
component) is the dedicated multi-value entry point, and is a later, separate
batch. Enabling `multiple` here today would silently turn on a state change
with no chips UI ever rendered to show it — a value changing with nothing on
screen to reflect it. Removing the prop at the type level, rather than just
warning against it in prose, is `SKILL.md` §3.4's "make the invalid
combination a type error, not a runtime footgun" applied to a whole feature
rather than one prop pairing.

### A real, Base-UI-caught bug: `Combobox.Label` labels the wrong element for this composition

An early draft exposed a `Label` part composing `BaseCombobox.Label`,
mirroring `Select.tsx`'s own `Label`. Rendering it logged, verbatim, Base
UI's own runtime warning: *"`<Combobox.Label>` labels `<Combobox.Trigger>`
only. When `<Combobox.Input>` is the form control, use a native `<label>` or
`<Field.Label>` instead."* The rendered DOM confirmed it: the accessible name
landed on the (`tabIndex={-1}`, purely decorative) trigger button, not on the
actual `<input>` a screen reader user types into. Base UI's own forms guide
(`docs/react/handbook/forms.md`, read directly) draws exactly this line:
`Combobox.Label` is for the *trigger-based* composition (input rendered
inside the popup); this component's own shape — input outside the popup,
matching `Select.tsx`'s sibling field-family look — is instead an *input
control*, labeled the same way `Input`/`NumberField`/`Autocomplete` already
are. This component therefore exposes **no `Label` part at all**: offering
one that is silently wrong for its own default composition would be worse
than offering none. A caller labels it with a native `<label htmlFor>` or
Base UI's own `Field.Label`, exactly as `Combobox.test.tsx`'s own fixture
does.

### `InputGroup` is a flex row, not Base UI's own absolutely-positioned reference layout

Base UI's own composition example (`forms.md`) absolutely-positions
`Clear`/`Trigger` over the input with hand-reserved trailing padding — a
shape that exists upstream because their input sits inside a taller,
label-including relative container. This component owns its own `InputGroup`
from scratch, so a plain flex row (`Input` as `flex-1 min-w-0`, `Clear`/
`Trigger` as fixed-size flex siblings) reaches the identical flush-right icon
layout with no padding-reservation arithmetic to keep in sync with `size` —
the exact kind of manual math `PasswordField.tsx`'s own `toggleInsetFor`
helper needs precisely because it cannot restructure `Input`'s pre-existing
box the way this component can restructure its own.

### Two real testing gotchas, found only by running the interaction tests

1. **The trigger's open is deferred one animation frame.** `ComboboxTrigger`
   opens via Floating UI's own `useClick(..., { event: 'mousedown' })`, whose
   `onMouseDown` handler wraps the actual `store.setOpen(...)` call in
   `frame.request(...)` (`requestAnimationFrame`) rather than applying it
   synchronously — verified by reading `useClick.mjs` directly after a
   synchronous assertion right after `fireEvent.mouseDown` found the list
   still closed. Opening via the trigger in a test therefore needs
   `await waitFor(...)`; opening via typing or an arrow key does not, since
   those apply through a different, synchronous code path
   (`store.state.setOpen(true, ...)` called directly inside `onChange`/
   `onKeyDown`).
2. **`fireEvent.change` does not look like real typing to this component.**
   `ComboboxInput`'s own `onChange` only opens the list on a change whose
   native `InputEvent.inputType` is present and not `'insertReplacementText'`
   — real typing always sets `inputType: 'insertText'`, but `fireEvent.change`
   dispatches a plain `Event` with no `inputType` at all, which Base UI reads
   as "autofill-like" and deliberately does *not* open the list for. Found
   after a `fireEvent.change`-based filter test rendered zero options; fixed
   by using `fireEvent.input(input, { target: { value }, inputType: 'insertText' })`
   instead. Neither of these is covered by `lessons-learned.mdc` yet and both
   are recorded there now, since they apply to `Autocomplete`'s own tests in
   this same batch too.

### How much visual language is shared with `Select` (and, transitively, `Menu`)

`Combobox.Item`'s classes, the popup's `z-popover`/`rounded-card`/
`shadow-elevation2` treatment, and the fixed-width reserved indicator column
are the exact same token strings `Select.tsx` uses, for the exact same
reason `Select.tsx`'s own README gives for sharing `Menu`'s: all three are
"choose a value from a floating list" surfaces, and a product where they
looked different for no reason would read as inconsistent. Duplicated,
not imported — `Select.Item` and `Combobox.Item` are backed by different
Base UI primitives with different props (`Select.Item` takes no `onClick`
override the way `Combobox.Item` legitimately can), so each keeps its own
implementation, matching strings rather than sharing a module, the same
choice `Menu.tsx`/`Popover.tsx`/`Select.tsx` already make independently for
their own popups.

`Combobox.Separator` is, like `Select.Separator`, a small Combobox-specific
file rather than a re-export of the generic `@base-ui/react/separator` —
verified directly rather than assumed identical to `Select`'s case, and its
documented surface again turned out to be exactly the generic separator's
own (`orientation`/`className`/`style`/`render`), so this component reuses
this package's own `Separator` directly here too.

## SOLID

Single responsibility: the floating-list mechanics, filtering wiring, and
tokens — nothing about what a company or a contact is, or how a remote
search actually runs (that belongs to whichever `entities/*` slice supplies
`items`/`filteredItems`). Dependency inversion: the entire state machine —
open/close, filtering, highlight, positioning — is Base UI's; this file
supplies only tokens, the `multiple` restriction, and the smaller compound
surface on top of it.
