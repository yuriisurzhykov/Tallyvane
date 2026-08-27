# accordion

The FAQ block, and documentation sections (`COMPONENTS.md`'s own words for
this row) — Tier 0. It knows how to coordinate several disclosures so that
opening one can close the others; it knows nothing about what a question or
a section actually is.

## What needed doing

A single `Collapsible` (built alongside this component, in the same batch)
covers one uncoordinated disclosure — a table row's expand caret. A FAQ
page or a documentation sidebar needs several disclosures that *agree with
each other*: by default, opening a second question closes the first, so the
page does not grow without bound as a reader works through it. Nothing in
`shared/ui` coordinated more than one `Collapsible` before this component
existed, and building that coordination by hand at every FAQ-shaped call
site would mean re-deriving the same "close the others" state machine each
time.

## What was actually done

Thin compound wrapper over `@base-ui/react/accordion`: `Root`, `Item`,
`Header`, `Trigger`, `Panel` — five parts, matching Base UI's own five real
parts exactly rather than collapsing any of them, since (unlike `Menu`'s
fixed `Portal → Positioner → Popup` nesting) each of these varies
independently at a real call site: `Item` carries `value`/`disabled`,
`Header` exists specifically to give `Trigger` a heading role, and `Panel`
is a sibling of `Header`, not a child of it.

`multiple` (default `false`) is passed straight through, not reimplemented:
confirmed by reading `AccordionRoot.js` directly, `multiple: false` makes
every `handleValueChange` call replace the whole open-value array with
`[newValue]`, which is the real single-open behaviour a FAQ page wants, not
an approximation built from watching `onValueChange` and closing siblings
by hand.

`Root` is re-exported directly with no wrapping `<div>`, matching
`Collapsible.Root`'s own reasoning one directory over: Base UI's own doc
comment confirms it renders a real `<div>`, but that div needs no visual
treatment since `Item`s already stack correctly in block flow. `Item` gets
exactly one token decision — `border-b border-border-subtle last:border-b-0`
— so a list of questions reads as one continuous, divided block with no
group-level container needed on `Root` at all, the same way a plain HTML
list needs no wrapping box to look like a list.

## Judgment call: `Trigger` defaults to a full-width, spread-out header

`Collapsible.Trigger` (this batch's other disclosure component) deliberately
leaves layout open — no `w-full`/`justify-between` — because its own
`COMPONENTS.md` row names two competing shapes (a narrow table-row caret and
a full header) with no single right default. `Accordion`'s row names only
one shape ("the FAQ block, and documentation sections"), so `Trigger` here
bakes in `flex w-full items-center justify-between`: a label on one edge, a
caller-supplied indicator on the other, which is what every accordion this
component will actually be used for wants by default.

## Judgment call: no roving arrow-key focus, verified rather than assumed

The brief for this batch explicitly asked for "arrow-key movement between
items... where the real Base UI primitive provides them — verify
empirically, don't assume." For `Accordion`, it does not: `AccordionTrigger.js`
attaches no keydown handler at all, and `AccordionRootState.orientation`'s
own `@deprecated` doc comment says why — the ARIA Authoring Practices group
[removed roving focus from the accordion pattern](https://github.com/w3c/aria-practices/pull/3434),
and this installed Base UI version already reflects that update. Every
trigger is simply a normal tab stop, reached the same way any other button
on the page is. `Accordion.test.tsx`'s own keyboard test asserts this
directly — pressing `ArrowDown` on a focused trigger does nothing — rather
than skipping keyboard coverage for this component the way it would look if
the (incorrect) assumption had gone unverified.

## The `render`-prop indicator, instead of a `group-data` selector

A rotating chevron that tracks open state needs to read the *trigger's* open
state from a *different* element (the icon inside it). The usual CSS
answer is a `group`/`group-data-*` Tailwind convention, which this codebase
does not use anywhere yet (verified by search, not assumed) — introducing
one for a single icon would be a new, unreviewed styling convention for a
single call site. Base UI's own function-form `render` prop already solves
this without one: `state.open` is available directly inside
`Accordion.Trigger`'s `render={(props, state) => ...}` callback, per
ADR-031's render-prop composition model, which is exactly how
`Accordion.stories.tsx`'s `WithIndicator` story implements it.

**2026-08-27 — the height animation was a snap, for two stacked reasons.**
`transition-geometry` and `h-(--accordion-panel-height)` were already on
`Panel`. That was not enough. First, `frontend-web` (unlike Storybook) did
not `@source` this package, so the JIT height class never reached the app
stylesheet — only `@utility` rules such as `transition-geometry` did. Base
UI's `getAnimationType` saw a non-zero duration, attempted a CSS height
transition, and nothing consumed `--accordion-panel-height`. Second,
`data-[starting-style]:h-0` compiled to nothing even after the scan was
fixed: `h-0` reads Tailwind's spacing scale, which the adapter clears
(`Drawer.tsx` already documents that `inset-0` is the same trap). The
0-height pin is now numeric `height: 0` while `transitionStatus` is
`starting`/`ending`, the same geometry-zero Drawer uses. Padding moved off
the panel onto the inner content so `height: 0` can actually be 0.

## SOLID

Single responsibility: coordinating which of several disclosures are open
and their shared tokens, nothing about what a question or a documentation
section is. Open/closed: a sixth item is new `Item`/`Header`/`Trigger`/
`Panel` JSX at the call site, never a new prop or branch here. Liskov
substitution: every `Item` behaves identically regardless of how many
siblings it has or whether `multiple` is set — the coordination lives in
`Root`'s context, not in any individual item's own logic. Dependency
inversion: open/close state, the single-vs-multiple replacement rule, and
`aria-expanded`/`aria-controls` wiring are all Base UI's; this file owns
only tokens and the one deliberate `Trigger` layout default explained above.
