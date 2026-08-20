# input

Single-line text. Tier 0.

## What needed doing

Every text field in the product needs visible, consistent styling — and,
more specifically, needed *fixing*: `Field`'s own stories originally
rendered a bare `<input>` inside it, and it was reported as effectively
invisible on screen. Root-caused via live browser `getComputedStyle`
inspection to Tailwind's preflight reset stripping all default input styling
(`background: transparent`, `border-width: 0`, `padding: 0`) — not a bug in
`Field` itself, but the absence of any component supplying real, visible
input styling on top of that reset. `Input` is that fix, and
`Field.stories.tsx` was updated to demo it instead of a bare `<input>` once
it existed.

## What was actually done

A thin styling wrapper over Base UI's own `Input` (ADR-031). Its source is
literally `<Field.Control {...props} />` — it's designed to sit directly
under `Field.Root`, or, as here, as `Field.Control`'s own `render` target,
so it composes cleanly into this package's `Field` (or a bare
`Field.Root`/`Form` with no `Field` wrapper at all) without a second,
competing control implementation.

Ships real, visible styling deliberately: background (`surface-inset`,
matching `KeyboardKey.tsx`'s own precedent for a "receptacle" control),
border, padding, radius, and a focus ring — exactly the properties
preflight zeroed out. Sizes are the shared `control` height roles
(`sm`/`md`/`lg`), matching `Button`'s and `IconButton`'s vocabulary for the
same word — deliberately not the native `size` attribute (a character-width
count), which is dropped in favor of the semantic one since the two would
otherwise be a real type collision on the same prop name.

Disabled styling keys off `data-[disabled]:`, mirroring `Button`'s and
`Toggle`'s convention, since `Input` sets the same `data-disabled` attribute
Base UI emits everywhere else. Invalid styling keys off `aria-invalid:`
rather than `data-invalid:`, though — `Field.Control` guarantees the former
reaches the rendered element even when a caller sets it directly with no
`Field.Root` ancestor at all (this component's own tests do exactly that);
`data-invalid` only ever gets computed from a real field's validity state.

## SOLID

Single responsibility: visible input styling, nothing about validation
logic or what the text means. Dependency inversion: focus handling and the
underlying `Field.Control` wiring are Base UI's; this file only supplies the
tokens that make the control visible and legible.
