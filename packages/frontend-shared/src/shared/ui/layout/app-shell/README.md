# app-shell

Sidebar plus main region — Tier 2, `COMPONENTS.md` §5's `AppShell` row, and
the composition root every console and admin screen is meant to render
inside.

## What was needed, and why nothing existing would do

Before this, every real screen (`LandingPage` aside — it is public marketing
chrome, not app chrome) rendered alone, full-bleed, with no persistent
navigation: `/today` had no link to any other screen because nowhere on the
page held one. `SidebarNav` and `TopBar` each solve one piece of that; this
component is the one place that actually puts a screen between them and
turns "two components exist" into "a real page has navigation."

## What was actually done — 2026-08-28

`AppShell` composes `SidebarNav` (left/top, depending on width), `TopBar`
(title plus optional actions) and a `<main>` region for `children`, plus a
`SkipLink` pointed at that region.

**`#main-content` lands here, not on each view.** `skip-link/SkipLink.tsx`'s
own comment already named "a future `AppShell`" as the owner of the actual
target id — this component is that future arriving, not a new decision.
Every screen that renders inside `AppShell` gets a working skip link for
free instead of wiring one by hand.

**`skipLinkLabel` is a required prop, not hardcoded English.** `SkipLink`
itself already takes its label as `children`, never hardcoding copy
(`SkipLink.tsx`'s own comment cites `COMPONENTS.md` §12 for exactly this);
`AppShell` sitting one layer above it and hardcoding the same string would
undo that rule at the next level up. The caller (a Tier 3+ view, which is
where an app's own i18n dictionary lives) supplies the real, translated
string.

## SOLID

Single responsibility: place three known regions relative to each other —
nothing about what fills any of them. Open/closed: `actions` and `children`
absorb new content without a change here. Dependency inversion: depends on
`SidebarNavItem`'s plain shape and `ReactNode` slots, never on a router or a
specific screen's own state.
