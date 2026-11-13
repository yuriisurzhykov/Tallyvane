# icon

The icon set behind one component — Tier 0, listed under "Marks and identity" in `COMPONENTS.md`.

## What needed doing

The product screens need a handful of glyphs: a microphone, a send arrow, a back chevron, a folder, a play
mark and a tick. Importing the icon library at each call site would let the library, not the design, decide
which glyphs exist, and would make swapping the set a hunt through every screen.

## What was actually done

`Icon` takes a `name` from a short union and renders the matching `lucide-react` glyph at the `stack`
spacing role, with a fixed stroke so every glyph weighs the same. It is always `aria-hidden`: an icon next to
text repeats the text, and an icon alone sits inside a control (`IconButton`) that already requires an
accessible name. A glyph that needs a name of its own is a different component. Adding a glyph is one line in
the map and one member of the union.
