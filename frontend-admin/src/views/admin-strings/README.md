# admin-strings

The admin's editable-strings screen — `views` layer, `ARCHITECTURE.md`
§12.9 and §13.3.

## 2026-08-28 — wired to `AppShell` and a real `KeyValueList`

Was an `EmptyState` placeholder. Now renders `AppShell` around a `KeyValueList`
of three static, mock strings under one namespace (`common`) — no strings
API exists yet to read a real namespace from.

## Not here

**Editing.** `KeyValueList` renders each value as plain `Text`, read-only —
`features/edit-strings` is still `.gitkeep`.

**Namespace grouping and the default-value comparison** §13.3 describes
(showing a translation next to the value it would fall back to). One
namespace, no comparison, until there is more than one real dictionary to
group.
