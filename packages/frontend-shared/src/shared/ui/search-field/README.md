# search-field

Text input with a clear affordance and debounce. Tier 0.

## What needed doing

Every known consumer (`SearchableList`, `filter-pipeline`, `global-search`
per `COMPONENTS.md`) needs to filter or query against typed text without
firing a request on every keystroke, and needs a way to clear the field in
one action rather than select-all-delete.

## What was actually done

Composes `Input` only — unlike `PasswordField`, this one does not reach for
`IconButton` for its clear affordance. `IconButton` is a square,
`control`-height-sized real button — the right visual weight for a toggle
beside a field, but heavier than the minimal, borderless clear glyph a
search box calls for everywhere else. The clear button is hand-rolled
instead, with the same rigor `component-authoring/SKILL.md`'s
volume/frequency threshold calls for at this scale: a real
`<button type="button">`, a real accessible name (`clearLabel`, required),
and a WCAG 2.2 24×24 CSS px hit target.

Fully controlled, deliberately with no uncontrolled `defaultValue` mode —
every known consumer already needs the current query in its own state to
filter or navigate with it, so an uncontrolled mode would only add the
controlled/uncontrolled discriminated union's complexity for a call site
that doesn't exist yet (YAGNI).

`onChange` keeps `Input`'s native `ChangeEventHandler` shape rather than a
`(value: string) => void` shorthand — a real, empirically-verified
constraint, not a style preference. `Field.Control` always composes its own
`onChange` with whatever the rendered element already carries, treating any
`on[A-Z]…`-named prop as a handler to chain by name alone regardless of
signature, then calls `event.currentTarget.value` on it. A value-shaped
`onChange` would still get chained the same way and crash the moment this
component is used inside `Field`. `onSearch(value: string)` is the separate,
debounced callback (default `debounceMs={300}`), read through a ref inside
the debounce effect so a caller re-rendering with a fresh inline callback
mid-keystroke never resets the pending timer.

`type` is fixed to `"text"` with `role="searchbox"`, not the semantically
closer `type="search"` — WebKit renders its own native cancel button once a
`type="search"` field has a value, which would sit right next to this
component's own clear button, two clear affordances for one field.

Clearing does more than reset local state: it bypasses the pending debounce
entirely (`onSearch("")` fires immediately, not after another `debounceMs`),
and because React tracks an input's value through its own patched setter to
decide whether a later native event counts as a "real" change, the clear
handler writes through the prototype's original, un-patched setter before
dispatching a manual `input` event — otherwise React would treat the
programmatic clear as a no-op and never call `onChange`.

## SOLID

Single responsibility: debounce timing and the clear affordance, nothing
about what the query does once it fires. Interface segregation: a consumer
only depends on `value`/`onChange`/`onSearch`, never on how the debounce
timer or the native-setter clear workaround are implemented internally.
