# landing

The public page at `/`. A short FAQ, not a marketing template: what it does,
why not a spreadsheet, what it does not do, who it is for. Built only from
`packages/frontend-shared` primitives (`Stack`, `Row`, `Logo`, `Text`,
`Accordion`). Copy comes from `src/app/i18n`, never from JSX literals.

## Why it is hand-coded

ARCHITECTURE.md §7 / §8.8 describes the real landing page as CMS-driven
`content.pages` / `content.blocks` rendered through `content-kit`. That
system is still an `export {}` stub. This view is a temporary placeholder
so `/` exists as a real, accessible page until the block renderer ships;
it will be replaced, not grown into the CMS.

## Why nothing existing could be reused

`src/views` was empty. The design-system primitives already cover every
piece of chrome this page needs — there was no missing layout component
and no reason to invent a `Hero` or `Faq` widget in `frontend-shared`
(those would be domain-shaped, and shared has none). The page is a view
because it is a whole screen, which is what this layer is for.

## What was actually done

**2026-08-27 — structure chosen by looking, not by a template.** A first
pass in a Cursor Canvas produced the generic hero / how-it-works /
feature-grid shape; it read as an AI portfolio page. A second pass
rendered three alternatives as a throwaway Storybook story against the
real tokens (`shared/ui/_scratch/LandingPreview.stories.tsx`, since
deleted): a README of paragraphs, a timeline of beats, and a four-item
FAQ. The FAQ was the one that did not look generated. There is no
separate feature grid, no stat row, no CTA, no header nav — nothing
those would link to exists yet.

The headline uses the `hero` text variant (fluid 36→56px), added to
`frontend-shared` during the same exploration after `display` (36px
fixed, the console ceiling) read as flat on a real marketing hero. See
`shared/ui/text/README.md`'s dated entry.

Strings go through `useStrings` (`common` + `landing` namespaces). The
lookup factory is in `frontend-shared`; the English dictionary is in
this app at `src/app/i18n`, so shared never learns the product's
vocabulary.

**2026-08-27 — headings and landmarks via `createElement`, not raw JSX.**
`no-restricted-syntax` bans lowercase JSX tags anywhere under `src/`, so
a view cannot write the `render={<h1 />}` that Text's own README documents,
nor the `<main>` landmark axe requires. `native.tsx` builds the same
elements with `createElement`. The heading helper is Base UI's function-form
`render` (not a pre-built element): a first pass of `createElement("h1")`
was measured at 16px/400 by the APCA suite because the variant class never
merged onto the tag. A future AppShell is the place header/main/footer
should move to, not a reason to invent heading primitives in shared today.

**2026-08-27 — FAQ is a client island, the rest of the page is not.**
Accordion is a compound object (`{ Root, Item, … }`) over Base UI state.
Marking that module `"use client"` and importing `Accordion.Root` from a
Server Component prerenders as `undefined` — Next's client-module interop
does not preserve nested function properties on a namespace object. The
same shape already exists on `Menu`/`Drawer`; they have not been imported
from a server page yet. Putting `"use client"` on `LandingFaq` pulls
Accordion into the client graph without marking the whole landing page
client, which is what the plan asked for.

**2026-08-27 — footer at the bottom of the viewport, FAQ height actually
animates.** Short content left the footer sitting under the FAQ with empty
screen below it: the page column was only as tall as its children. `Stack`
is already `flex flex-col`; `min-h-dvh` on the page column and `flex-1` on
`<main>` (via `Native`'s new `className`) is the sticky-footer pattern, not
a fixed overlay. The FAQ snap was not a landing bug. Accordion's height
classes never reached `frontend-web`'s stylesheet (`@source` was missing,
unlike Storybook), and `h-0` is not a class this theme has — see
`packages/frontend-shared/src/shared/ui/accordion/README.md`'s same-day
entry. Answer padding moved onto the inner `Text` so the panel can
collapse to height 0.

When `content-kit` lands, this directory is the thing the block renderer
replaces. The FAQ copy should move onto `content.pages` / `content.blocks`
rather than being rewritten in place here.

## SOLID

Single responsibility: compose the public homepage from shared primitives
and the app dictionary. It does not know how Accordion coordinates
open state, how `hero` is tokenised, or how `{name}` substitution works.
Open/closed: a fifth FAQ item is a dictionary key plus a row in
`LandingFaq`'s list, not a new page-level branch. The CMS replacement is
a deletion of this slice, not an extension of it — that is the honest
closed part, and it is why this README flags the placeholder status
instead of pretending the page is the long-term design.
