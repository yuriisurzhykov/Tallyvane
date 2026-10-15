# page-frame

Tier 2 — a page with a side column and a heading of its own, for a screen
that is read more than it is operated.

## What needed doing

`AppShell` keeps a thin top bar (`TopBar`: a small title and an `actions`
slot) because the console is many small screens. The admin's version screens
(ADR-101) are few and long, and want the title large at the head of the
content with a line of explanation beneath it and the page's primary act
beside it. A page that needs a heading element, a `main` landmark and an
`aside` cannot write them itself: raw elements are banned outside
`shared/ui`, so the frame has to be a primitive.

## What was actually done

`PageFrame` takes the side column as a slot (`sidebar`) and owns only where it
sits (a rail from `lg` up, a band above the content below it) and what
landmark it is (`aside`). The content column is `main#main-content`, the
target of the skip link, with a centred measure; the heading is the page's one
`h1`. `AppShell` and `TopBar` are unchanged, so the console is untouched.

Added 2026-10-15 for the redesign of the admin's version screens.

## SOLID

Single responsibility: place a slot, a heading and a content column. It does
not know what the side column holds (brand, navigation) — that is the app's
widget. Open/closed: a second app with the same shape reuses it by filling the
slot, with no change here.
