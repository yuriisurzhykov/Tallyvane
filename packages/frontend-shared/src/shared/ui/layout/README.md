# layout

Tier 2 — components that know where things sit, never what they are
(`COMPONENTS.md` §5). Grouped under one folder, unlike every Tier 0/1
primitive sitting flat under `shared/ui/`, because `COMPONENTS.md` §5
already names `shared/ui/layout/` as where this tier specifically lives —
worth its own folder because the layouts are a category of their own
purpose (structural placement) rather than one more visual primitive.

Each component keeps its own directory, its own public API (`index.ts`) and
its own dated `README.md`, exactly like every other `shared/ui` component —
this folder's `README.md` is a pointer, not a second copy of any of them:

- [`sidebar-nav/README.md`](./sidebar-nav/README.md)
- [`top-bar/README.md`](./top-bar/README.md)
- [`app-shell/README.md`](./app-shell/README.md)

Built 2026-08-28, first pass: `AppShell` composes `SidebarNav` and `TopBar`
around a `children` slot with a working skip link. `ThreeColumnLayout`,
`TwoPaneLayout`, `FocusLayout`, `ContentLayout`, `DocsLayout`, `PrintLayout`
and `CenteredLayout` — the rest of `COMPONENTS.md` §5's table — are not
built; add each the day a real screen needs its specific shape rather than
ahead of one (`SKILL.md` §4, Rule of Three).
