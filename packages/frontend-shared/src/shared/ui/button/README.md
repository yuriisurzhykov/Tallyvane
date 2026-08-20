# button

The base clickable action across the whole product — Tier 0, tokens only, no
domain knowledge of what it triggers.

## What needed doing

Every screen needs a button, and this codebase's own rule (`COMPONENTS.md`
§2, "Tier 0 may compose Tier 0") meant `IconButton`, `Menu`'s trigger, and
eventually form-submit actions would all want the same tone/size vocabulary
rather than each reinventing it. Nothing existing covered this: a plain
`<button>` carries no tokens, and Base UI's own `Button` primitive (once
confirmed to exist — see below) carries no visual opinion at all by design.

## What was actually done

Wraps `@base-ui/react/button` directly rather than hand-rolling activation
behavior via `useRender`. This was a real, documented correction mid-batch:
the original brief assumed Base UI had no dedicated `Button` export and
instructed building one the way `Text`/`IconButton` are built (bare
`useRender` + `mergeProps`). The installed package does ship a real `Button`
(`nativeButton`, `focusableWhenDisabled`, correct Enter/Space keyboard
activation even when `render` swaps the element for a non-`<button>`), and
per ADR-031 and `component-authoring/SKILL.md` §6 ("reuse Base UI for
anything with real interaction machinery"), the real primitive was used
instead — the same reasoning `Field`/`Fieldset` already apply. This is why
`IconButton`, built earlier the same batch under the old assumption, still
uses bare `useRender` and was deliberately left as-is rather than retrofitted
(see `icon-button/README.md`).

Four tones, not a boolean grid: `primary` (solid `interactive-primary` fill),
`neutral` (border only, no fill — matches the "Secondary action" swatch
already proven in `frontend-web/app/storybook/page.tsx`, so it reads as
bordered over whatever surface it sits on rather than a second competing
fill), `ghost` (borderless, fill only on hover), `danger` (solid
`statusDanger` fill with `textOnSolid` — the same "deep fill, light text"
pairing `themes/shared-roles.ts` documents, chosen so a destructive action
reads as a real button and not a status badge; no dedicated hover/pressed
shade exists for it in the token set, so state feedback there is opacity,
not an invented colour role). Sizes are the shared `control` height roles
(`sm`/`md`/`lg`), default `md`.

`loading` disables the button, sets `aria-busy`, and swaps the leading icon
slot for an inline spinner built from a scoped `<style>` tag with its own
`@keyframes` rather than Tailwind's `animate-spin` utility — the adapter
clears `--animate-*` to `initial` the same way it clears every other
namespace, so a theme-keyed `animate-spin` class would silently resolve to
nothing. A `<style>` element is excluded from the button's accessible-name
computation, so it can't leak into the label the way a stray text node
would.

## SOLID

Single responsibility: tone/size tokens and the loading-state affordance,
nothing about what an action does — every feature composes this rather than
this component knowing about "save" or "apply". Dependency inversion is the
concrete one here: keyboard activation, disabled semantics, and the
`render`-prop polymorphism contract all come from Base UI's real `Button`;
this file only supplies the visual layer on top, so a future upstream fix to
either concern needs no change here.
