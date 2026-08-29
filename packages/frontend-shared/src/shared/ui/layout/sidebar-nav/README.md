# sidebar-nav

The persistent list of destinations beside `AppShell`'s main region — Tier 2,
`COMPONENTS.md` §5's `SidebarNav` row.

## What needed doing

Console and admin both need a fixed list of links that stays on screen while
the page in the main region changes. Nothing existing covers this: `Row`/
`Stack` lay children out but know nothing about "current page," and `Link` is
Tier 0 inline prose text (underlined, meant to sit inside a sentence), not a
nav item's visual shape.

## What was actually done — 2026-08-28

`SidebarNav` takes a flat list of `{ label, href, isActive }` and renders one
`<a>` per item inside a labelled `<nav>`. `isActive` is computed by the
caller, not this component: a router-aware `SidebarNav` would make this
package depend on Next's `usePathname`, and `Link.tsx` already draws that
same line — routing decisions live one layer up, this component only renders
what it is told.

**Judgment call: text-only, not an icon rail, below `lg`.** The obvious
version of "collapse the sidebar on a narrow screen" is `SidebarNav`'s own
`--layout-sidebar-collapsed` token (64px, icon-only) already sitting in the
theme. Not built that way: `IconButton.tsx`'s own comment records `Icon`'s
API as still undecided, and a 64px column of unlabelled icons is a worse
navigation experience than no collapse at all if half of them are unclear.
Below `lg` (1024px, Tailwind's own default and already this project's
breakpoint set), the same items render as a horizontal, scrollable row of
full labels instead — comfortable to tap through on a phone (ARCHITECTURE.md
§1.4: phone is a real, if secondary, use case for admin and console edits),
and it does not block on a component that does not exist yet. Revisit once
`Icon` is designed — the 64px collapsed token stays reserved in the theme for
that day.

**No `Link` reuse for items.** `Link.tsx` is styled as inline prose
(underlined, `text-interactive-primary-text`) — correct for a sentence, wrong
for a nav row. Nav items render a plain `<a>` directly, which is exactly what
`packages/frontend-shared/src/shared/ui/**` is for (the raw-JSX ban's own
`ignores` entry).

## SOLID

Single responsibility: render a list of destinations and mark the current
one — nothing about what routing library produced `href` or `isActive`.
Open/closed: a new destination is a new array entry, not a code change here.
Dependency inversion: depends on a plain data shape (`SidebarNavItem`), never
on `next/navigation` or any router.
