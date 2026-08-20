# toast

The failure channel for optimistic writes, and the host of undo — Tier 0.
`ToastRegion` is the region that owns queueing (mount it once, near the app
shell root); `useToast()` is the hook any descendant calls to enqueue,
update or close a toast without knowing — or caring — whether the queue is
local state, a global store, or, as it is here, Base UI's own
`ToastProvider`. That is the `state`/`actions`/`meta` split
`.cursor/skills/component-authoring/SKILL.md` §3.2 asks for, applied to a
genuinely queue-shaped problem rather than to a component with two stable
props and nothing structural to vary.

## The shape, and why

```ts
state:   { toasts: ToastSnapshot<Data>[] }   // the current queue, read-only
actions: { add, close, update }               // mutate the queue
meta:    { manager: ToastManager<Data> }      // a stable handle, usable outside React
```

`state`/`actions` map almost directly onto Base UI's own `useToastManager()`
— that hook already *is* the state/actions split internally; this file's
job is repackaging its return value into this project's own shape (`tone`,
not Base UI's bare `type`, per the hard "tone, not colour" rule in
`COMPONENTS.md` §12) and giving every UI part in this tree one interface to
depend on instead of the concrete Base UI hook, which is the actual
dependency-inversion benefit: swapping the underlying engine later touches
one file.

`meta.manager` is the one genuinely new piece, and it exists because
`useToastManager()` does not expose the manager instance it wraps. It is a
real gap, not a convenience: "the failure channel for optimistic writes"
realistically means a mutation hook's global error handler (a query
client's `onError`, say) firing a toast from code that is not necessarily a
descendant of wherever `<ToastRegion>` happens to be mounted — but it is
always within the same provider boundary, the exact "shared state does not
require shared JSX nesting" point `patterns.md` §2 makes for `Composer`.
`ToastRegion` creates one manager internally by default, or accepts a
pre-created one via its `manager` prop for a caller that wants the same
handle available at module scope, outside any component.

`Data` defaults to `Record<string, never>` — no fields at all — rather than
Base UI's own permissive `any`. `shared` holds no domain types
(`COMPONENTS.md` §2); leaving the default open would let a Tier 3+ caller
smuggle a domain-shaped payload through a Tier 0 toast without this
component ever deciding to allow it. A caller that genuinely needs custom
data instantiates `useToast<MyData>()` explicitly.

## A real bug this batch's own test coverage almost missed

The first draft of the per-toast action button read:

```tsx
<BaseToast.Action className="..." {...toast.actionProps} />
```

which looks like the obvious way to wire up the action's `onClick`. It is
wrong: `Toast.Action` already resolves its own `children` and `onClick`
from `toast.actionProps` internally (`ToastAction.js` reads it straight off
context, exactly like `Title`/`Description` do for their own content) — so
this spread wired the *same* handler into the merged prop chain twice.
Clicking "Undo" fired the callback twice. The Vitest suite's first draft
asserted a boolean flag (`undone === true`), which cannot distinguish
"called once" from "called twice" and stayed green through the bug; a real
Chromium click in `toast-keyboard.spec.ts` produced two "Restored" toasts
from one click and caught it for real. Fixed by removing the spread
entirely — `<BaseToast.Action className="..." />`, no other props — and the
Vitest test was rewritten to count calls, not just check a flag, so the
same class of bug cannot pass silently again.

## Another thing only a real browser could show

`Toast.Close` sets `aria-hidden={!expanded && !hasFocus}`, with `hasFocus`
as local state on the button itself — deliberately unreachable by a stray
tab stop until the toast (or the viewport's "expanded" stack view) actually
has focus. `Toast.Action` carries no such gate. Both `ToastRegion.test.tsx`
and `toast-keyboard.spec.ts` document the real mechanics of getting past it
for a test, since a naive `getByRole` query on the Close button fails
exactly the way a screen reader would skip it — correctly, not as a bug.

## SOLID

Single responsibility: the queue and its rendering, nothing about what a
toast is *for* — tone is a vocabulary this project already has everywhere
else, not a domain concept. Interface segregation: a UI part that only
needs to fire a toast depends on `actions.add`, never on the whole hook's
return value. Dependency inversion, stated above: real, not decorative —
this is the one file that would need to change if the underlying toast
engine ever did.
