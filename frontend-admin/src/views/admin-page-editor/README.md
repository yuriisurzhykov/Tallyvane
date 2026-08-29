# admin-page-editor

The admin's page editor — `views` layer, `ARCHITECTURE.md` §12.9.

## 2026-08-28 — a real form stands in for the three-column editor

Was an `EmptyState` placeholder. `COMPONENTS.md` §5 specifies
`ThreeColumnLayout` (blocks / generated field form / live preview) here,
composing `content-kit`'s `block-editor` widget — neither exists yet
(`widgets/.gitkeep`, `content-kit`'s block widgets are all `export {}`
stubs). A single-column form (`Field` + `Input` + `TextArea`) stands in so
the screen is real and useful today rather than another empty state,
without pretending to be the three-column shape it is not.

**Not reading the route's own `id`.** `app/(admin)/pages/[id]/page.tsx`
does not pass it through — mock content is the same regardless of which
page id is visited. Wiring the real id through is straightforward the day
a real page-fetch replaces the mock array; deferred rather than half-done
now.

## Not here

**Block editing, revision history, publish/unpublish** — `features/edit-page-blocks`,
`features/restore-revision`, `features/publish-page` are all still
`.gitkeep`.
