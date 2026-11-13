# product-shell

The frame of the product's screens: a header, one centred column, an optional dock — Tier 2.

## What needed doing

`AppShell` is a console: a sidebar of places and a bar with a title. The product is not a console. Its home
is one next step with the route behind it, and the design canvas the screens follow draws it as a single calm
column with the wordmark above and the "what's new?" field pinned below. A sidebar made the product look like
the thing it explicitly is not.

## What was actually done

`ProductShell` takes the wordmark, an optional way back, optional header actions, an optional dock, and a
width (the reading column, or the wide one for a document beside its notes). The two widths are layout
roles (`--layout-column-max-width`, `--layout-column-wide-max-width`), not numbers in a class. The dock is
sticky with a fade above it, so content scrolling under the composer dissolves instead of being cut. It owns
the `#main-content` landmark, the same id `AppShell` uses, so a skip link means the same thing in both frames.

`AppShell` stays for the admin app, where a list of places is exactly what is wanted.
