# ADR-101. The version screens lead with the policy in force and explain every field

## Status

Accepted. Chosen by the owner on 2026-10-15 while redesigning the screens of slice 7c (plan with mockups: `https://claude.ai/artifact/EZnYR79fXbNbBMTq7Y7coB`;
diagrams in `docs/frontend/04-admin-version-screens.md`). Refines ADR-099 (what the screens do is unchanged) and keeps ADR-091
(no modal windows). The look follows the design canvas "Tallyvane auth screens".

## Context

The screens of 7c did what ADR-099 decided, and the owner found them hard to use. A version was one long sentence; the version
in force differed from the others by a small badge; the buttons of a row had no common width; in the drawer a ticked box on a
factor did not say what ticking meant, and the three limits were three fields without a visible reason, so a person who had not
built the form would not know what to type where.

## Decision

**The version in force is the anchor of the screen.** A card at the top shows it: a name, who put it in force and why, the steps as
a chain (or the three lifetimes as large figures) and one line of limits. It has no buttons. Every other version is a quiet row
(number, name, who and when) with one act of the same size in every row and a "Details" button that opens the row in place.

**Every control says what it is.** A factor of a step is a switch with a hint and the words "Accepted" or "Not accepted", under the
question "Which of these lets a person pass this step?"; whom the step is asked of is two options in words. Limits are collapsed to
one line; open, each is a row with a name, a sentence and the only input at the right, with the value in force beside it ("Now: …").
A fault on a limit opens the part by itself. A line "Result" at the top of the drawer follows every edit.

**The activation panel gains the differences.** The panel loads the consequences as before and now also shows how the chosen version
differs from the one in force, drawn as in the canvas. The version builds the differences (`changesFrom`) from the two versions the
screen already holds; there is no new request and no new contract.

**The frame is the app's own.** A widget `admin-shell` draws the sidebar (logo, "admin", two groups) and the page heading with the
primary action. The shared `AppShell` is not changed; the frame is built on a new `PageFrame` of `frontend-shared`, which the admin needs because it may not write `main`, `aside` or a heading element itself.

## Consequences

- All five admin pages use `admin-shell`; the three CMS placeholders change only an import.
- `frontend-admin` has two new display components (the chain of steps and the three figures), one module for differences and a
  method on each version; the entities' data handling and the 7b requests are untouched.
- The console and `AppShell` keep their top bar. If the owner later wants the console on the same frame, that is a change of
  `frontend-shared` of its own.
- `frontend-shared` gains `PageFrame` and an optional `label` on `SidebarNav`; its theme's stacking roles are renamed to the namespace Tailwind reads, which makes `z-modal` and the rest real classes.
- About three dozen strings are added to the app's dictionary.

## Alternatives considered

**Changing the shared `AppShell` and `TopBar` so both apps get the canvas layout.** One frame for both sites. Rejected for this
change: it moves the console, its tests and Storybook, and widens a task about two admin screens; it is a reasonable follow-up.

**One list as on the canvas board S9, the version in force being its first row.** Closer to the board and simpler (one layout of a
version). Rejected: the current policy would sit behind a click, and after a rollback the version in force moves down the list.

**Keeping the checkboxes and a grid of three fields, adding hints.** The smallest change. Rejected after the owner's review of the
mockups: a checkbox on a factor still has to be read as "this one lets a person pass", and three fields side by side still leave a
person guessing which number is which.

**Drawing the lifetimes as a timeline.** A picture of idle, absolute and fresh spans. Rejected: three figures with a sentence each
read faster and need no scale.
