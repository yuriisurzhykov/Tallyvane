# slider

One value dragged or stepped from a numeric range — Tier 0. Single-thumb
only, per `COMPONENTS.md`'s own row: "no dual-thumb range variant until a
real call site needs one."

## What needed doing

The weekly application goal (`COMPONENTS.md`'s own example) and any future
single-number-in-a-range field all need dragging, keyboard stepping (arrow
keys, Home/End, Page Up/Down) and value formatting — real interaction
machinery `.cursor/skills/component-authoring/SKILL.md` §6 says to reuse
rather than hand-roll.

## What was actually done

Composes `@base-ui/react/slider`'s `Root` → `Control` → `Track` →
`Indicator` + `Thumb` (ADR-031). Base UI computes the indicator's fill
width and the thumb's position internally from the current value — this
component supplies only the rail thickness (`h-inline-tight`, an actual
registered spacing role used for its real height, not borrowed the way
`Dot`'s `size-inline` borrows one for a diameter), the thumb diameter
(`1.25rem`, the same tokenless constant as `Checkbox`'s/`Radio`'s own box
size), and tokens for each part's `data-*` state.

`Value` is fixed to `number` in this component's own exported type, not
passed through as a type parameter the way `Radio`'s is: Base UI's real
`SliderRootProps<Value extends number | readonly number[]>` would
otherwise let a caller pass an array of values and silently get a
multi-thumb slider this component only ever renders one `Slider.Thumb`
for — locking the generic here turns that into a compile error instead of
a confusing runtime shape mismatch.

### Two things found only by rendering and inspecting the real accessibility tree

**The accessible name belongs on `Slider.Thumb`, not `Slider.Root`.**
Every other passthrough wrapper in this batch applies `aria-label` to the
one interactive element it wraps directly — the first draft of this one
did the same, leaving `aria-label` to flow straight through to
`Slider.Root`. `Slider.test.tsx`'s first run showed why that is wrong:
`Root` renders `role="group"`, and the actual `role="slider"` element is
the native `<input type="range">` nested inside `Thumb`, which had no
accessible name at all. Base UI's own multi-thumb design expects each
thumb's name through `Thumb`'s own `aria-label`/`getAriaLabel` for exactly
this reason (a range slider's two thumbs need two different names) — this
component now reads `aria-label`/`aria-labelledby` off its own incoming
props and re-applies them to `Thumb` instead.

**`focus-within`, not `focus-visible`, for the same structural reason.**
The part that receives real DOM focus is that same visually-hidden native
input, sized to `width: 100%; height: 100%` of the visible thumb `<div>`
around it (confirmed by reading `SliderThumb.js` directly — Base UI's own
comment says this is "so that VoiceOver's focus indicator matches the
thumb's dimensions"). `:focus-visible` on the outer, visible thumb never
matches, since that element itself is never what's focused.
`focus-within:focus-ring` is used instead, matching `Combobox.tsx`'s own
`InputGroup` (built independently, in parallel, for the identical
hidden-input-inside-a-visible-wrapper shape) rather than introducing a
second technique such as `has-[:focus-visible]` for the same problem.

**A native `disabled` input, not a JS-level guard, is what actually blocks
keyboard stepping.** Confirmed by reading `SliderThumb.js`: unlike
`Checkbox`/`Radio`/`Switch`, there is no `if (disabled) return` inside its
keydown handling, only around pointer/drag start — arrow-key blocking
relies entirely on the browser's own native refusal to focus or accept
keyboard input on a disabled form control. `Slider.test.tsx`'s disabled
test asserts the native `disabled` attribute directly rather than firing
`ArrowRight` and checking nothing moved, since `fireEvent.keyDown` in
jsdom does not reproduce that native blocking — a jsdom-only false
negative would not indicate a real bug here.

## SOLID

Single responsibility: rail/thumb geometry and state tokens, nothing about
what the number means. Dependency inversion: the actual drag/keyboard/value
state machine is Base UI's; this file needs no change if that underlying
mechanism is ever swapped, aside from the two accessible-name/focus wiring
decisions above, which are genuinely this component's own responsibility
to get right on top of it.
