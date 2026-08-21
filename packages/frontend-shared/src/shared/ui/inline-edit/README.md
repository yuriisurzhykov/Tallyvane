# inline-edit

Click to edit, 400ms debounce, optimistic write. Tier 1 — **the default write
interaction of the product**, per `COMPONENTS.md` §4, not a special case
reached for occasionally.

## What needed doing

ARCHITECTURE.md §12.9 bans modal windows outright and states the product's
one write behaviour in a single sentence: "Автосохранение через 400 мс после
последнего нажатия клавиши, оптимистично, с откатом при ошибке" (autosave
400ms after the last keystroke, optimistic, rolling back on failure). Every
editable field in the console — an interest rating, a parsed job field, a
compensation line — needs the same click-to-edit shell and the same
debounce/optimistic/failure machinery behind it. Building that per field
would mean re-deciding the debounce timing, the optimistic-exit behaviour and
the failure channel at every call site; `COMPONENTS.md`'s own line for this
component ("not a special case") is exactly the argument against that.

Nothing existing in `shared/ui` covered this before now: `Field`/`Input`
handle a single control's own styling, not the click-to-view/edit toggle or
the save timing around it, and no debounce/autosave state machine existed
anywhere in the package.

## What was actually done

Two pieces, split because the debounce/optimistic/error state machine is not
specific to "click to edit" — a future `Switch` that autosaves immediately
(`debounceMs={0}`, no click-to-edit toggle at all) needs the identical
pending/error/retry machinery, not a copy of it wearing a text-field shape.
The "will this be reused?" question `development-methodology.mdc` asks
before writing anything shared answers itself here.

- **`shared/lib/use-debounced-autosave.ts`** — a pure state machine. Takes
  `value`, `onSave`, `debounceMs` (default 400); returns
  `status: "idle" | "pending" | "error"`, `error`, `lastSavedValue`, `retry`
  and `flush`. No UI, no `shared/ui` import. See that file's own doc comment
  for the full account of the one real design decision it had to make on its
  own — reproduced in outline below since it's the reason `InlineEdit`'s own
  exit behaviour looks the way it does.
- **`shared/ui/inline-edit/InlineEdit.tsx`** — the click-to-view/edit toggle,
  Enter/Escape/blur keyboard contract, and the `onError` failure channel,
  built on top of that hook.

### The rollback decision, and why it shapes both files

ARCHITECTURE.md's one sentence does not say what "rolling back" means once a
save actually fails. Two readings were real candidates:

1. Revert the displayed value to the last known-good one automatically.
2. Surface the failure and the means to retry it, and leave the displayed
   value exactly where the user left it — the caller (which owns the actual
   source of truth once a real mutation exists) decides what, if anything,
   to do about it.

**(2) is what's built**, for a reason that is about ownership, not taste:
`useDebouncedAutosave`'s own `value` is a prop handed to it, not state it
holds — there is nothing inside the hook to substitute in its place if it
decided to "revert." Doing (1) honestly would require the hook to own the
value (`useState` returning `[value, setValue]`), which was considered and
rejected: `InlineEdit` already needs its own local copy of the draft (to
seed the editor when it opens, to restore it exactly on Escape) — a second,
competing copy inside the hook would just be the same value held twice,
disagreeing the moment either one changes without the other knowing.
`InlineEdit` reflects this same decision on its own side: after a commit, it
keeps showing the value the user just typed (not the stale prop, and not an
automatic revert) regardless of whether the save behind it eventually
succeeds or fails — a failure surfaces through `onError`, which hands the
caller both the raw error and a `retry` bound to the exact value that failed
(the only copy of it lives inside this component; a caller's own toast
action could not reconstruct it). This is also why `InlineEdit`'s trigger
gets a `border-status-danger` outline while status is `"error"`: something
in the display has to be able to say "this hasn't actually saved" for a user
who dismissed or never saw the toast, since nothing here ever un-shows what
they typed.

### Two hook actions beyond the plan's literal wording: `flush` and `lastSavedValue`

The originating plan asked the hook for "status... and a `retry`." Building
`InlineEdit`'s own Enter/blur-commits behaviour on top of it needed two more
things, added deliberately rather than invented quietly:

- **`flush()`** — save the current value now, bypassing whatever debounce
  wait remains, a no-op if nothing has changed since the last confirmed
  save. Enter and losing focus both need to finalize immediately rather than
  wait out up to 400ms of a debounce the user has already signalled they're
  done with; routing that through the hook (rather than `InlineEdit` calling
  the raw `onSave` prop itself, bypassing the hook) is what keeps every
  attempt — debounced, flushed, or retried — going through the same status/
  error tracking and the same `onError` wiring, instead of duplicating that
  logic a second time.
- **`lastSavedValue`** — the last value a save actually confirmed. A direct
  consequence of resolving the rollback question as (2) above: if this hook
  will not decide what "known good" means for the caller, it still owes the
  caller enough information to decide for itself, and "the last confirmed
  value" is the one fact only this hook has.

### Why the trigger's accessible name is the rendered value plus `editLabel`, not `editLabel` alone

`IconButton`'s own precedent sets `aria-label` unconditionally, correctly,
because it has no other content a screen reader could read. `InlineEdit`'s
trigger is different: `renderValue`'s output is real, meaningful content
(the interest rating, the job title), and overriding it with `aria-label`
would silently take that away from anyone using a screen reader. Instead,
`editLabel` is appended via `VisuallyHidden` *inside* the button alongside
`renderValue`'s own output, so the computed accessible name is both —
`"Backend Engineer Edit job title"` in this file's own test — rather than
either alone.

### A judgment call `useDebouncedAutosave`'s own tests exist to pin down: superseded in-flight saves

A value can change again while a previous save is still in flight (typing
past one debounce window into the next). The hook tags every attempt with
an incrementing id and ignores a resolution or rejection whose id is no
longer current — otherwise a slow, now-superseded save resolving *after* a
newer one could overwrite `status`/`lastSavedValue` with stale information.
Verified directly: `use-debounced-autosave.test.ts`'s own
"a superseded in-flight save does not overwrite the status of a newer one"
resolves the two attempts out of the order they were dispatched and asserts
the second one, not the first, wins.

### A deliberate, documented gap: no built-in "undo to the previous value" after a commit

`InlineEdit` ships `retry` (via `onError`) as the only caller-facing recovery
action after a failure — not a separate "revert to what it was before this
edit" action. The plan's own wording only asked for retry, and no named call
site (`COMPONENTS.md` §6's interest rating, fit rating, parsed job fields,
compensation lines) needs anything more yet; adding one now would be
building past Rule of Three on a guess. A caller that genuinely needs a full
reset of `InlineEdit`'s internal state has the standard React escape hatch
available without a new prop: changing the `key` passed to `InlineEdit`
remounts it from scratch.

### A narrower, single-user-product simplification, stated rather than silently assumed

`InlineEdit` re-syncs its local draft from the `value` prop only while **not
editing** — a background update landing mid-edit does not fight the user's
own in-progress keystrokes. It does not additionally guard against the
external `value` changing to something else entirely while a save this
component itself triggered is still pending or erroring; ARCHITECTURE.md's
own framing of this project ("a personal system for running a job search")
makes a genuinely concurrent external writer to the same field a
non-scenario in practice, so building that guard now would be speculative
complexity for a race this product does not have. Noted here as a scoped
decision, not an oversight, in case that framing ever changes.

## SOLID

Single responsibility: `useDebouncedAutosave` owns timing and status,
nothing about what "editing" looks like; `InlineEdit` owns the click-to-edit
toggle and the keyboard contract, nothing about debounce internals or what a
given value even means (no domain noun anywhere in this file, per
`COMPONENTS.md` §2). Open/closed: a new call site with a wildly different
editor (a rating widget instead of a text field) is a new `renderEditor`
function at the call site, never a new branch inside this component.
Interface segregation: `renderValue`/`renderEditor` each receive exactly the
data they need (a value, or a value plus `onChange`/`commit`/`cancel`),
not one large props bag both share. Dependency inversion: `InlineEdit`
depends on `useDebouncedAutosave`'s returned `status`/`retry`/`flush`
contract, never on how debouncing or attempt-superseding is implemented
underneath it — the one file that would need to change if that
implementation ever did is `use-debounced-autosave.ts` itself.
