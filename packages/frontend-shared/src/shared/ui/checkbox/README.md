# checkbox

A tickable box for an independent yes/no choice — Tier 0.

## What needed doing

`Checkbox` needed the same treatment `Input`'s own README already documents
for text fields, only worse: Tailwind's preflight strips a native
`<input type="checkbox">` down to something that at least still paints a
faint browser-default box, but Base UI's `Checkbox.Root` renders a bare,
fully unstyled `<span>` with no native appearance to fall back on at all.
Without real visible styling here, every checkbox in the product would be
invisible against the page — checked and unchecked indistinguishable from
each other and from empty space.

## What was actually done

Thin styling wrapper over `@base-ui/react/checkbox`'s `Checkbox.Root` +
`Checkbox.Indicator` (ADR-031): checked/indeterminate state, keyboard
activation and disabled semantics are entirely Base UI's, verified against
the installed `CheckboxRoot.d.ts` rather than assumed. This component reads
the `data-checked`/`data-indeterminate`/`data-disabled` attributes Base UI
emits and maps them onto tokens — a visible border and inset background at
rest, a solid `interactive-primary` fill with an `text-on-accent` glyph on
top once checked or indeterminate.

The box is `--control-box` (1.25rem), the same role `Radio`'s ring,
`Slider`'s thumb and `RatingScale`'s dots read. A named rem constant used
to be the lint exemption; that hole is closed. The glyphs are sized with
`h-(--control-icon) w-(--control-icon)` rather than Lucide `size={14}`.

The tick and dash glyphs (`lucide-react`'s `Check`/`Minus` — already a
regular dependency of this package, used the same direct way
`PasswordField.tsx` already imports `Eye`/`EyeOff` from it, rather than
Drawer's older placeholder-`<svg>` precedent) both stay mounted whenever
`Checkbox.Indicator` itself is mounted, switched with a `group-data-`
selector rather than a JS conditional on the incoming `indeterminate` prop.
This is required, not a style preference: a "select all" parent checkbox
inside a `CheckboxGroup` gets its indeterminate state computed internally
by Base UI from the group's own ticked values, not from a literal
`indeterminate` prop this component was called with — the data attribute
Base UI actually puts on the DOM is the one value guaranteed to match what
is really rendered, confirmed against `CheckboxGroup.js`'s own
`useCheckboxGroupParent` wiring rather than assumed from the type
declarations alone.

No `label` prop, matching `Input`'s precedent rather than `IconButton`'s:
a checkbox is conventionally labelled externally (via `Field`, or plain
text at the call site with `aria-labelledby`), unlike an icon-only button
that never has adjacent text to associate with. See "Judgment calls" in
this batch's authoring report for the open note on `Field`'s current
label-above-control layout not yet suiting a checkbox's more natural
side-by-side label placement — unresolved because no real call site needs
it yet.

## SOLID

Single responsibility: checked/indeterminate/disabled tokens and the tick
glyph, nothing about what the choice means — every feature composes this
rather than this component knowing about "remote only" or "requires
sponsorship". Dependency inversion: the actual state machine (controlled
vs. uncontrolled, keyboard activation, indeterminate computation inside a
group) is Base UI's; this file only reads the data attributes the contract
already promises, so it needs no change if that underlying mechanism is
ever swapped.
