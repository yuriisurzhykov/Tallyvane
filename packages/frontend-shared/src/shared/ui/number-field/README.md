# number-field

Numeric with increment, decrement and scrub. Tier 0.

## What needed doing

Every count, quantity or rate in the product (years of experience, number of
rounds, a rating denominator) needs a real numeric control — one that clamps
to a sensible range, steps by keyboard or button, and, per this batch's own
brief, is verified rather than assumed to already support all three of
increment, decrement and scrub before any of them gets hand-built. Reading
`@base-ui/react/number-field`'s own `.d.ts` and installed docs directly
confirmed all three: `NumberField.Increment`/`Decrement` are real stepper
button parts, and `NumberField.ScrubArea`/`ScrubAreaCursor` implement
pointer-drag scrubbing (with an optional Pointer-Lock custom cursor) —
nothing here needed hand-rolling.

## What was actually done

`Root`/`Group`/`Input`/`Increment`/`Decrement`/`ScrubArea`/`ScrubAreaCursor`,
backed entirely by Base UI. `Root` is a bare re-export — it renders a plain
`<div>` managing state (value, `min`/`max`/`step`, scrubbing) with no visual
decision of its own, the same reasoning `Menu.Root`/`Popover.Root` already
established for the identical shape.

### Why the bordered "box" lives on `Input`, not on one container merging all three parts

Base UI's own reference demo merges `Decrement`/`Input`/`Increment` into one
bordered box, with the two buttons manually stripped of their inner border
(`border-r-0`/`border-l-0`) to avoid a doubled seam. That shape was considered
and rejected here: this design system's `rounded-control` radius is a shared
token used identically on `Input`'s own box and on `IconButton`'s square, and
nesting an `IconButton` flush inside a `Group` sharing that same radius risks
either a visible doubled corner or (if solved with `overflow-hidden` on the
`Group`) clipping the `focus-ring` utility's own `outline-offset` on whichever
stepper button sits at the group's edge — a real accessibility regression
that cannot be safely ruled out without rendering in an actual browser and
inspecting the result, which this batch's overnight run has no path to do.
Recorded here as the wrong turn caught before it was written, not after: the
chosen shape instead gives `Input` its own complete, independently-bordered
box (literally the same box `Input.tsx` ships, reimplemented here since
`BaseNumberField.Input` — not this package's `Input` — must remain the
literal rendered element carrying Base UI's parsing/formatting/clamping),
with `Increment`/`Decrement` as separate, fully-rounded `IconButton`s beside
it. No shared border, so no corner-nesting problem to solve at all.

`text-numeric` (this package's own tabular-figures-plus-slashed-zero
typography role — `Numeric.tsx`'s own choice for "every salary, count and
date in a column") replaces `Input.tsx`'s `text-body` on the field itself:
digits that align and an unambiguous zero benefit an editable numeric field
in a way free-form text does not, and reusing an existing token here is DRY,
not a new decision.

`Increment`/`Decrement` compose this package's own `IconButton` via `render`
— the exact shape `Popover.tsx`'s own `PopoverClose` already established for
"a Base UI behavioural part that must render as a real, sized, toned icon
button," rather than a fourth reimplementation of a square icon button.
`tone="neutral"` (bordered, visible at rest) rather than `"ghost"`: unlike a
dismiss affordance or a menu item's leading icon, increment/decrement are two
of the three behaviours this component's own one-line purpose names
explicitly, so they stay discoverable without requiring a hover first.
Default glyphs are `lucide-react`'s `Plus`/`Minus` — the same reasoning
`PasswordField.tsx` already used to pick `Eye`/`EyeOff` from the same,
already-installed dependency rather than waiting on `COMPONENTS.md` §13's
still-open `Icon` API decision, since a plus/minus pair is a near-universal
glyph unlikely to be revisited once that decision lands. Each stepper button
requires a `label` string prop, mirroring `IconButton`'s own "an icon-only
control with no name is not a valid button" rule exactly — this is `COMPONENTS.md`
§12's "copy arrives as props below Tier 3" applied to the one piece of English
text a numeric stepper genuinely needs.

Invalid styling on `Input` keys off `data-[invalid]`, not `aria-invalid:` —
the opposite of `Input.tsx`'s own choice, for the opposite reason. `Input.tsx`
sits behind this package's own `Field` wrapper, whose `Field.Control`
guarantees `aria-invalid` reaches the rendered element even with no
`Field.Root` ancestor. `NumberField.Root` cannot go through that wrapper (see
below), so the only invalid signal it can rely on is the one Base UI's own
`NumberFieldInputDataAttributes` documents: `data-invalid`, "present when the
number field is in an invalid state (when wrapped in `Field.Root`)."

### This package's own `Field` does not fit `NumberField` — verified, not assumed

`@base-ui/react`'s own forms guide (`docs/react/handbook/forms.md`, read directly
rather than guessed) shows `NumberField.Root` — and `Select.Root`/
`Combobox.Root`/`Autocomplete.Root` alongside it — placed as a **direct
child** of a bare `Field.Root`, labelled by a `Field.Label` **sibling**
inside that same tree, never threaded through `Field.Control`. This
package's own `field/Field.tsx` does the opposite: it forces its single
`children` prop through `BaseField.Control render={children}`, a shape built
for a single simple element (`Input`) whose whole surface Base UI's own
`Field.Control` augments directly with `id`/`aria-describedby`/`aria-invalid`.
`NumberField.Root` is a multi-part `<div>` wrapping several separately
focusable elements — augmenting *it* with those attributes would put them on
the wrong element entirely, never reaching the actual `<input>` inside.
Concretely: **this package's own `Field` component does not yet support
compound, multi-part controls**, `NumberField` included. Composing `NumberField`
correctly today means nesting it directly under a bare `@base-ui/react/field`
`Field.Root`, exactly as Base UI's own forms guide shows — not this package's
`Field`. Extending `Field` (or introducing a second pattern) to close this
gap is out of scope for this batch (the guardrails forbid touching `Field.tsx`)
and is flagged in this batch's authoring report for a deliberate decision
later, since three more compound controls (`Select`, `Combobox`, `Autocomplete`)
share the exact same gap.

### The one non-obvious piece of internal plumbing: a `size` context

`Group`, `Input`, `Increment` and `Decrement` all need to agree on one
cosmetic value — their shared height — and Base UI's own anatomy keeps them
as four separate parts. Repeating a `size` prop on three of them at every
call site was rejected as needless friction for a single cosmetic value;
instead, `Group` writes `size` into a small, **unexported** context that
`Input`/`Increment`/`Decrement` read, defaulting to `"md"` so every part still
renders correctly on its own with no `Group` ancestor, matching Base UI's own
"any part usable in isolation" philosophy. This is not the `state`/`actions`/
`meta` compound contract `SKILL.md` §3.2 describes — there is no behaviour
here, only a height role — so a full compound API would be ceremony; a
lightweight context confined to this one file is proportionate.

## SOLID

Single responsibility: the field's visible box, its two stepper buttons'
tokens, and nothing about what the number means — a rating, a headcount and
a step count are all just `NumberField` underneath, per `RatingScale`'s and
`CompensationBreakdown`'s own eventual composition of it. Dependency
inversion: parsing, formatting, clamping, keyboard/wheel/scrub interaction
and Pointer-Lock cursor handling are entirely Base UI's; this file supplies
only tokens, the `IconButton` composition, and the `size` plumbing above.
