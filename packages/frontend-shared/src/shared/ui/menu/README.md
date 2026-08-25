# menu

A dropdown menu with roving focus, typeahead, and a shortcut column. Tier 0.

## What needed doing

Row actions, the shortcuts palette, and any other "click a trigger, get a
list of choices" surface all need the same keyboard-navigable list — arrow
keys, typeahead, Home/End, Escape-to-close with focus return — without every
call site re-deriving that behavior by hand.

## What was actually done

`Root`/`Trigger`/`Popup`/`Item`/`Separator`, backed entirely by
`@base-ui/react/menu` (ADR-031): open/close, roving `tabIndex`, arrow-key and
typeahead navigation, outside-press/Escape dismissal, and focus return to the
trigger are all Base UI's. `Root` and `Trigger` are bare re-exports with no
tokens of their own — `Root` renders no DOM at all, and `Trigger`'s actual
look always comes from whatever a caller composes into it via `render`
(typically this package's own `Button`), mirroring `Popover.tsx`'s
independently-established convention for the identical pair.

`Popup` bundles Base UI's fixed `Portal → Positioner → Popup` nesting behind
one part — the three-level structure never varies by caller, so collapsing
it removes ceremony without losing anything. `z-popover` lives on the
Positioner, not the Popup: verified by rendering and inspecting the DOM that
Base UI's `Positioner` is the element actually carrying `position: fixed`,
while `Popup` itself has none — a `z-index` has no effect on a statically
positioned element per the CSS spec, so the class has to sit on whichever
element is actually positioned for cross-overlay stacking to work once a
second portalled overlay (a `Toast`, say) exists at the same time.
`align="start"` is this component's own opinionated default over Base UI's
own `"center"` — a menu conventionally hangs from its trigger's leading
edge, unlike a general-purpose `Popover`.

`Item.shortcut` renders through this package's own `KeyboardKey` (Tier 0
composing Tier 0, `COMPONENTS.md` §2) as one slot, not a multi-key array —
no known call site needs a visually separated key-combo yet. A disabled item
stays reachable by arrow-key navigation and is only blocked from activating
(verified by reading Base UI's own `MenuRoot` source, which passes an empty
`disabledIndices` list to its list-navigation logic), matching the ARIA
Authoring Practices guidance that a disabled item may stay perceivable while
not actionable.

A real, verified testing gotcha found while writing the dedicated Playwright
keyboard spec: jsdom's `fireEvent.click` defaults to `MouseEvent.detail: 0`,
which Base UI cannot distinguish from a real Enter/Space key activation —
both fire with `detail: 0`. A plain jsdom "click" therefore silently
exercised the keyboard-open path (which pre-highlights the first item), not
a real mouse click (which highlights nothing until an arrow key is pressed).
Caught only because the real-Chromium Playwright spec disagreed with the
passing Vitest test; both suites now pass `{ detail: 1 }` explicitly to
simulate a genuine mouse click. Recorded in `lessons-learned.mdc` since it
applies to any future component test asserting mouse-vs-keyboard-specific
behavior.

## 2026-08-25 — typeahead e2e sent `d` before the popup held focus

CI failed `menu-keyboard.spec.ts`'s typeahead case with Playwright's
`toHaveAttribute("data-highlighted", "")` reporting `Expected: ""` /
`Received: serializes to the same string` / `unexpected value "null"`. The
Duplicate item was in the DOM for the whole 15s timeout (`tabindex="-1"`,
no `data-highlighted`); the highlight never arrived. That error text is
Playwright comparing an empty-string value to a missing attribute, not a
component bug — the jsdom test that `fireEvent.keyDown`s the menu itself
still passes.

Root cause, read from the installed `@base-ui/react@1.7.0`:
`FloatingFocusManager` moves focus onto the popup in a layout effect, then
`queueMicrotask`, then `enqueueFocus`'s `requestAnimationFrame`.
`locator.click()` returns before that frame. `page.keyboard.press("d")`
goes to whatever currently has focus; typeahead's `onKeyDown` lives only on
the trigger and the popup. If focus is in transit (trigger already blurred,
popup not yet focused), the key is dropped. `expect()` then retries the
assertion for 15s and never re-sends the key. ArrowDown/Home/End hide the
same gap because `useListNavigation` handles those keys on the trigger
(`openOnArrowKeyDown: true`); a letter has no such fallback. Base UI's own
`onMatch` also no-ops unless `open` is already true.

Fix: wait until the menu is visible and focused, then `menu.press("d")` —
the locator focuses the popup before sending the key, matching what the
jsdom test already did by dispatching on `role="menu"`.

## SOLID

Single responsibility: menu-list interaction and its tokens, nothing about
what any given item does when activated. Dependency inversion: the entire
state machine — open/close, focus, navigation — is Base UI's; this file
supplies only tokens and the smaller compound surface on top of it.
