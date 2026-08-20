# radio

One option in a mutually-exclusive set — Tier 0. Always used inside a
`RadioGroup`, the same way every `Toggle` is meant to live inside a
`ToggleGroup`.

## What needed doing

Same problem `Checkbox`'s own README documents, for a shape that needs two
elements instead of one: Base UI's `Radio.Root` is a bare, unstyled `<span>`
with no native appearance, and a radio's selected state is conventionally a
smaller filled dot inside a larger ring — not a fill of the whole shape
(which would read as a solid toggle button, not a radio).

## What was actually done

Thin styling wrapper over `@base-ui/react/radio`'s `Radio.Root` +
`Radio.Indicator` (ADR-031): selected state, keyboard activation and
disabled semantics are entirely Base UI's. Verified by reading
`RadioRoot.js` directly rather than trusting the `.d.ts` comments alone:
Radio deliberately activates on Space only — its own `onKeyDown` calls
`event.preventDefault()` on Enter specifically so a stray Enter inside a
form doesn't get treated as a click, matching native
`<input type="radio">` semantics.

`1.25rem` for the outer ring is the same literal, same reasoning, as
`Checkbox`'s own `BOX_SIZE` — see that file's comment on why it is a named
constant instead of a token, and why the two files each keep their own
copy rather than sharing an import. The inner dot (`0.625rem`, exactly
half) is sized to read clearly inside the ring without visually touching
its border.

Generic over `Value` (defaulting to `string`, the common case — work mode,
seniority), left unconstrained rather than narrowed to `extends string`
the way `Toggle`'s own generic is: Base UI's real `RadioRootProps<Value>`
is unconstrained too (confirmed against the installed `.d.ts` — unlike
`ToggleProps`, whose Base UI type genuinely is `Value extends string`,
since a toggle group's selected state is a `string[]` of pressed ids). This
is what lets `RadioGroup<number>`/`Radio<number>` work with a numeric
value directly, with no string-conversion layer, which `RatingScale`'s own
brief effectively requires — though `RatingScale` ended up wrapping the
raw Base UI primitives itself rather than this file; see its README for
why.

## SOLID

Single responsibility: selected-state tokens for the ring and dot, nothing
about what the option means. Dependency inversion: the actual state
machine — which radio in the group is selected, keyboard activation,
roving focus — is entirely `RadioGroup`'s and Base UI's; this file only
reads the `data-checked`/`data-disabled` attributes the contract promises.
