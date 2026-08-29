# today

The console's landing screen — `views` layer, `ARCHITECTURE.md` §12.5.

## 2026-08-28 — wired to `AppShell` and a real widget

Was an `EmptyState` placeholder since the console's first scaffold. Now
renders `AppShell` (sidebar, top bar, skip link) around `widgets/today-actions`
with three static, mock recommended actions.

**Why the sidebar lists only "Today."** `ARCHITECTURE.md` §12.2 plans eight
routes for this app; seven are still `.gitkeep`. `SidebarNav` renders
whatever `items` it is given without checking that the target exists — a nav
item pointing at `/pipeline` today would be a dead link presented as a real
one, not a preview of a future screen. Add each item the day its route is
real, not before.

**Why the actions are static.** No backend endpoint exists yet to fetch a
real recommended-actions list from — the mock array is a placeholder for
that response shape, not a design decision about presentation. `TodayActions`
itself takes the list as a plain prop, so replacing this with a real fetch
later is a change to this file, not to the widget.

## Not here

**Sorting by urgency.** Left to whoever eventually owns real job/application
data — a presentational widget branching on business meaning is exactly what
`registry-owns-branching` exists to catch elsewhere in this codebase.

**Focus mode.** `COMPONENTS.md` §5's `FocusLayout` is a separate, deliberately
sparse layout for Today's other mode, not this screen with fields hidden —
not built yet.
