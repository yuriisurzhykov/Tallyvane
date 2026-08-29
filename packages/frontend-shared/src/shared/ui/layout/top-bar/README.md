# top-bar

The strip above `AppShell`'s main region — Tier 2, `COMPONENTS.md` §5's
`TopBar` row.

## What needed doing

Every screen in the console and admin needs to say, unambiguously, which
screen it is — the one real `<h1>` on the page, since `Text`'s heading
variants default to `<span>` precisely because no Tier-0/1 component can
know whether its usage is that page's one true heading (`Text.tsx`'s own
comment). Something has to own the decision "this is the page title" once,
so individual views stop guessing at it themselves.

## What was actually done — 2026-08-28

`TopBar` renders a `<header>` (an implicit `banner` landmark, verified by
test rather than assumed) holding `title` as a real `<h1>` via `Text`'s
`render` prop, and an optional trailing `actions` slot.

**Judgment call: `actions` as a plain slot, not `search`/`theme`/`density`/
`account` as four named props.** `COMPONENTS.md`'s full `TopBar` spec names
those four; none of today's call sites need any of them (YAGNI — Rule of
Three, not one guessed-at future). A generic `ReactNode` slot is exactly
`Panel`'s own `header`/`footer` precedent for "optional named region, not
worth a compound API," and it does not have to change shape when the first
of those four actually gets built — it becomes an element passed into
`actions`, not a new prop on this component.

## SOLID

Single responsibility: one heading, one optional trailing region — nothing
about what the title says or what the actions do. Open/closed: `actions`
absorbs whatever a screen needs next without a code change here.
