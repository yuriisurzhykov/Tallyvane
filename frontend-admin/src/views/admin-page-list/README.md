# admin-page-list

The admin's page-list screen — `views` layer, `ARCHITECTURE.md` §12.9.

## 2026-08-28 — wired to `AppShell` and a real `DataTable`

Was an `EmptyState` placeholder. Now renders `AppShell` around `DataTable`
with three static, mock pages (title, slug, draft/published status, updated
date) — no CMS content API exists yet to list real pages from.

## 2026-08-28 — narrow-screen columns now scroll instead of shrinking

Reported directly against a real phone: all four columns here share equal
`DataTable` flex weight, and at phone width that squeezed each one down to
an unreadable sliver — first surfacing as overlapping text (a real
`DataTable` bug, since fixed), then, once that was fixed, as heavy "…"
truncation on every cell (not broken, but not useful either). Resolved at
the `DataTable` level, not here: columns now floor at a real `minWidth`
instead of shrinking indefinitely, and the row scrolls horizontally past
that floor rather than compressing further — see `data-table/README.md`'s
matching dated entry for the mechanism. Nothing changed in this file; the
fix is entirely in the shared component moving one column's fixed
"Landing" from illegible to reachable-by-scroll on the same viewport.

## 2026-08-28 — `"use client"` is required here, not optional

`DataTable`'s `columns` need a `cell` render function and `getRowId` needs a
real function value — both cross from this view into `DataTable.Root` as
props. React's server/client boundary refuses a function prop passed from a
Server Component into a Client Component at build time ("Functions cannot
be passed directly to Client Components"), so this view could not stay a
Server Component the moment it needed either. Marked `"use client"` for
that reason, not by default — the other three admin views pass no function
props to anything and stay Server Components.

## Not here

**Row actions** (edit, publish, restore revision) — `features/edit-page-blocks`,
`features/publish-page`, `features/restore-revision` are all still
`.gitkeep`. Clicking a row does nothing yet; it should navigate to
`admin-page-editor` once that is a real decision, not an assumed one.

**Filtering by status.** Three rows do not need it yet — Rule of Three,
`SKILL.md` §4.
