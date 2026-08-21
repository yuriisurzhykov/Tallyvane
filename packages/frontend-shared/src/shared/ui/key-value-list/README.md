# key-value-list

Label-and-value pairs — Tier 1, composing `Stack`/`Row`/`Text` (Tier 0). Per
`COMPONENTS.md` §4: "parsed job fields, the compensation breakdown."

## What needed doing

A parsed job's fields (location, seniority, work mode, source) and a
compensation breakdown (base, signing bonus, annual target) are both the
same shape underneath: a short label, then a value, repeated several times,
read top to bottom. Left unshared, each screen re-decides the same three
things every time — how much space separates the label from the value, how
the two are vertically centred against each other when one is taller (a
`Badge` next to plain text), and what colour the label takes versus the
value. None of those are business decisions; all three are exactly the
"know tokens, nothing else" scope Tier 1 is for.

## Why nothing existing could be reused instead

`Row` already centres a label and a value on the cross axis, but only for
one pair at a time — a caller would still have to loop, decide the gap
between rows, and decide the label's colour role at every call site.
`Stat` is the nearest sibling shape but solves a different problem: one
emphasized number with an optional delta, not several equally-weighted rows
of paired facts. Building `KeyValueList` from `Stack`/`Row`/`Text` rather
than duplicating any one of their jobs is the direct application of
`COMPONENTS.md` §2's "a primitive may compose another primitive that
carries no domain knowledge and imposes no competing visual decision."

## What was actually done

One prop, `items: readonly { label: string; value: ReactNode }[]`, mapped
into one `Row` per item inside an outer `Stack`. `label` is wrapped in
`Text` internally (`small`, `color="secondary"`) so every row's label reads
consistently without the caller having to remember to style it; `value`
stays an unwrapped `ReactNode` — plain text, a `Badge`, a `Money` — exactly
`Stat`'s own "lay it out, don't re-style it" boundary, applied to the same
kind of slot.

**Judgment call: `justify-between` on each `Row`.** The plan's own brief
names only "`Row`'s own cross-axis-centred convention" explicitly, which
covers vertical alignment but not the horizontal arrangement between label
and value. The two live options were: `justify-between` (label left, value
pushed to the row's far edge — the definition-list reading most existing
key-value UI already uses) or a fixed-width label column via a raw
dimension (rejected outright: `COMPONENTS.md` §11 already bans a
`className` used for anything but layout and position, and a hand-picked
pixel width for "the label column" is exactly the kind of arbitrary
dimension the token lint rules exist to catch). `justify-between` needs no
new token and reads correctly with `Row`'s own `items-center` still doing
the vertical half of the job unmodified, so it was the one addition made
here — listed for review as a real layout decision, not a purely mechanical
one, since it is not spelled out verbatim in the brief.

`item.label` is used as the React list key rather than the array index —
correct here specifically because a label is this component's own natural
identity for a pair (`ARCHITECTURE.md`'s job-field and compensation-line
call sites do not repeat a label within one list), and it survives
reordering or filtering the way an index would not.

No real wrong turn to report: the `Stack`-of-`Row`s shape was the first
candidate considered, matched the brief's own explicit mention of `Row`'s
convention, and held up under `COMPONENTS.md` §2's composition test on
first read.

## Why it's understandable, scalable, extensible

Understandable: the data shape (`label`, `value`) is the entire mental
model — nothing to infer about how it renders once you know it is "a
label and a value, once per item." Scalable: every screen with paired
facts (parsed job fields today; compensation lines, contact affiliations,
and more once entities exist) reaches the same component, so a future
spacing or colour change updates every one of them at once. Extensible: a
future "copy value" affordance or a hover state, if a real call site ever
needs one, composes at the `value` slot the caller already controls,
without a change to this component at all.

## SOLID

Single responsibility: turning an array of label/value pairs into rows,
nothing about what any pair actually represents. Open/closed: this
component's only "variation point" is `items`' own length and content —
supporting a new kind of pair is a new `value` a caller passes in, never a
new branch inside this file. Dependency inversion: depends on `Stack`'s,
`Row`'s and `Text`'s public `gap`/`className`/`variant`/`color` contracts,
never on their internal class-name resolution.
