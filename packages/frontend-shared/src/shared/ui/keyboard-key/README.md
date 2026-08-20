# keyboard-key

Renders a key or a key combination in the visual shape of a physical
keycap — Tier 0, and per `COMPONENTS.md`, "required by the action menu,
which must show its shortcuts." Every rendered shortcut in this product
goes through this one component rather than each menu inventing its own
`<kbd>` styling.

## What needed doing

A keyboard shortcut needs a visual treatment distinct from ordinary body
text — a bordered, inset chip that reads as a physical key, not a sentence.
HTML already supplies the correct semantic element for this (`<kbd>`), and
nothing else in this tier repurposes it: `Text` has the wrong semantics
(no text style says "this is a key"), and there was no existing chip
component to borrow either. Composing a shortcut sequence (e.g. Ctrl+K) is
the other real requirement here, and it needed to not become this
component's job — see below.

## What was actually done

No Base UI primitive backs this — a `<kbd>` needs no interaction behaviour
to delegate, only one fixed class string built from existing surface,
border, radius and typography roles (`bg-surface-inset`, `border-border-subtle`,
`rounded-chip`, `text-caption`, `text-text-secondary`). No new component
token was needed; the existing roles already covered the look.

Unlike `Text` and `VisuallyHidden`, this component takes no `render`
prop — a deliberate omission, not an oversight. A key cap's semantics never
vary by context the way a text style's element does: it is always naming a
key, so there is nothing for a caller to opt into by swapping the tag.

The other deliberate omission is a `keys` array prop for showing a
combination. Instead, the caller composes two `<KeyboardKey>` instances
with a separator between them, exactly as the `Combination` story and its
matching test do (`<KeyboardKey>Ctrl</KeyboardKey>+<KeyboardKey>K</KeyboardKey>`).
This keeps the component from having to own a decision — what separator
glyph, and whether every combination even uses the same one — that isn't
about rendering a single key. There is no wrong turn to report: this is
the shipped design, and it was evidently reached by choosing composition
over a configuration array from the start, matching
`.cursor/skills/component-authoring/SKILL.md` §3.1's general preference for
explicit composition over parameterized configuration.

## SOLID

Single responsibility: rendering one key cap — nothing about what a
shortcut or a combination means, which stays the action menu's job to
arrange. Open/closed: a combination of any length is an extension by
composition (more `<KeyboardKey>` instances plus a caller-supplied
separator), never a new prop on this component. Interface segregation:
every prop this component exposes (`children`, `className`) is one it
actually uses — no unused `render` or `tone` slot inherited from its
siblings just for consistency's sake, since neither would ever vary here.
