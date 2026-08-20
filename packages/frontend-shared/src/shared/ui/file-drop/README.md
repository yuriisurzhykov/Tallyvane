# file-drop

Drag-and-drop file selection, single file — Tier 0. Résumés and other
media uploads (`COMPONENTS.md`'s own row for this component) are the known
call sites; the upload itself is not this component's job.

## What needed doing

Drag-and-drop has no keyboard equivalent at all — there is no keyboard
gesture that means "drag this file here" — so a drop zone that is only
operable by dragging fails WCAG 2.1.1 outright. Nothing existing in
`shared/ui` covers file selection at all yet, and no Base UI primitive
exists for it either (`COMPONENTS.md`'s own `Base` column for this row is
`—`).

## What was actually done

A real, natively-operable `<input type="file">`, visually hidden through
this package's own `VisuallyHidden` (Tier 0 composing Tier 0 — carries no
domain knowledge, `COMPONENTS.md` §2) rather than `display: none`, so Tab
still reaches it and native Enter/Space activation opens the OS file
dialog with no JavaScript of this component's own needed for that part —
a load-bearing requirement, not a nice-to-have: the whole point is a
keyboard path that does not depend on any of this component's own event
handling being correct. A visible "Browse" button is a second, independent
way to reach the same dialog; the dashed drop-zone `<div>` itself is
deliberately **not** in the tab order — it is a mouse/pointer convenience
(clicking anywhere in it also opens the dialog), not a third redundant tab
stop for an already keyboard-reachable action.

No Base UI primitive backs this, genuinely justified by the
volume/frequency threshold `.cursor/skills/component-authoring/patterns.md`
§7 sets out: one drag-and-drop surface in the whole product, not the tenth
`Menu`/`Combobox`/`Dialog` in this library — hand-rolling with full rigor
(real drag events under test, a real keyboard path, this README) is the
legitimate call here, the same reasoning that section's own two worked
examples make.

### A real bug, caught by testing genuine drag events instead of trusting the design

`openFileDialog` calls `inputRef.current.click()` to open the dialog from
the drop zone's own `onClick` and from the Browse button. `HTMLInputElement
.click()` dispatches a real, bubbling click event — and the hidden input
sits *inside* the drop zone `<div>` it is triggered from, so that synthetic
click bubbles straight back up into the same `<div>`'s own `onClick`,
calling `openFileDialog` a second time. `FileDrop.test.tsx`'s first draft
of "opens the file dialog when the drop zone is clicked" caught this for
real — `HTMLInputElement.prototype.click` was spied on and recorded two
calls per real click, not one. The fix is exactly the pattern Base UI's
own hidden inputs already use for the identical problem (`CheckboxRoot.js`'s
own `inputProps.onClick: (event) => event.stopPropagation()`, read
directly while researching this batch, with the comment "Clicks dispatched
on the input from the root's `onClick` ... are an implementation detail and
must not reach ancestors") — the hidden input's own `onClick` stops
propagation.

### Why drag state is a counter, not a boolean

`dragenter`/`dragleave` fire again for every child element the pointer
crosses while still inside the drop zone (the icon, the instruction text,
the Browse button) — a naive boolean toggled on each event flickers the
active-drag styling off the instant the pointer enters any of them, even
though it never actually left the zone. A depth counter
(`dragDepthRef`, incremented on `dragenter`, decremented on `dragleave`,
only clearing the active state at zero) fixes this. `FileDrop.test.tsx`
dispatches a real `dragenter`/`dragleave` pair on a child element between
entering and leaving the zone itself, specifically to prove the counter
survives that nested crossing rather than trusting the fix by inspection.

### Single source of truth for the selected file, and its real limitation

The selected `File` lives in this component's own internal state, reported
upward through `onFileChange` — there is no `value`/`defaultValue` pair the
way `Input`/`Checkbox`/etc. have. This is not an oversight: a browser will
not let JavaScript set a file input's value to an arbitrary `File` (a
security restriction, not a Base UI or React limitation), so a genuinely
controlled file input is not implementable at all. One real, documented
consequence: a drag-and-dropped file is reflected in this component's own
state and reported via `onFileChange`, but the underlying native
`<input>`'s own `.files` is only ever populated by the Browse path — a
caller integrating with a native `<form>` submission (rather than reading
`onFileChange` and building a `FormData` by hand) would not see a
drag-and-dropped file in that form's own submitted data. No known call
site needs native form submission for this yet (per this batch's brief,
the actual upload is a higher tier's job); flagged under "Judgment calls"
in this batch's authoring report as a real limitation to revisit if one
does. Likewise, there is no way to clear the selection from outside this
component today — a caller that needs to reset it after a successful
upload elsewhere can remount via `key`, the standard React idiom for
exactly this.

### What is deliberately not here

Upload progress and error states are out of scope per this batch's brief —
an actual upload happens at a higher tier once a real feature needs one.
The `accept` prop is passed straight to the native input, which only
narrows what the Browse dialog *offers*; a browser does not enforce it on
a drop, and this component does not add its own validation on top — a
caller wanting to reject a dropped file of the wrong type does so in its
own `onFileChange`, at the tier that actually knows what "wrong type"
means for that call site.

## SOLID

Single responsibility: the drop zone, drag-state tracking, and file
selection — nothing about what happens to the file afterward, matching the
"presenter, not the mutation" split every Tier 0/1 component in this ladder
holds to. Dependency inversion: the real interaction machinery this
component *does* reuse (line-clamping the filename, the clear button's
accessible name) comes from `Truncate`/`IconButton`, composed rather than
reimplemented.
