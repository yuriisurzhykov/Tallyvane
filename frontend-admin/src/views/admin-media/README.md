# admin-media

The admin's media library — `views` layer, `ARCHITECTURE.md` §12.9.

## 2026-08-28 — wired to `AppShell` and a real grid

Was an `EmptyState` placeholder. Now renders `AppShell` around a two-column
`Grid` of four static, mock media items (file name, how many pages use it)
— no media API exists yet to list real uploads from. Thumbnails are a
placeholder `Surface` with the word "preview," not a real image — nothing
uploaded yet to preview.

**Two columns, not a responsive count.** `Grid`'s own `columns` prop is a
fixed number (`Grid.tsx`'s own comment: no token exists for a column count),
so this is the deliberately conservative choice that stays usable on a
phone (ARCHITECTURE.md §1.4) rather than the wider count a desktop-only
grid could afford. Revisit once `Grid` grows a responsive column option or
a dedicated wrapper exists.

## Not here

**Upload, alt text, usage-location detail.** `features/upload-media` is
still `.gitkeep`.
