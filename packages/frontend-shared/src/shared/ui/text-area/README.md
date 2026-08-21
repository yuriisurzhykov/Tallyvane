# text-area

Auto-growing multi-line text input. Tier 0.

## What needed doing

A multi-line text control that grows with its content, styled consistently
with `Input`, without a JS-driven measurement/mirroring hack — the classic
"auto-resizing textarea" approach most component libraries reach for.

## What was actually done

No Base UI primitive exists for this (`COMPONENTS.md`'s Base column is `—`
here) — a plain `<textarea>` plus the native CSS `field-sizing: content`
property does the whole job with zero JavaScript. This degrades safely where
unsupported (Firefox and Safari as of mid-2026): an unrecognized CSS
property is simply ignored rather than cascade-blocking, leaving the fixed
`min-height` and native `resize: vertical` (`resize-y`) as the floor and the
manual-resize affordance respectively — so the control is never actually
broken on those browsers, just non-auto-growing.

`min-height` is a `calc()` over `--ds-text-body-line`,
`--ds-semantic-spacing-inline-tight` and `--ds-border-hairline` — three
lines of body text plus this component's padding and hairline border, not
a pre-summed rem figure. A named constant used to be the lint exemption;
that hole is closed. `Truncate`'s line-clamp count remains a JS integer
because it is a count, not a CSS length.

A late, deliberate correction to `COMPONENTS.md`: this component's `Env`
column originally guessed `client`, on the assumption that auto-growing
text needs a measurement effect. The final `field-sizing: content`
implementation is pure CSS with zero hooks of its own, so it was flipped to
`server` once the actual implementation settled — a real instance of "when a
first hypothesis is wrong, say so and show the correction"
(`development-methodology.mdc` §5).

Unlike `Input`, disabled state uses the native `disabled:` pseudo-class, not
`data-[disabled]:` — nothing sets a Base UI-style `data-disabled` attribute
on a plain `<textarea>`, so there's no data attribute to key off of here.
`aria-invalid:` still works exactly like `Input`'s, set directly by a caller
or a future `Field` textarea variant.

## SOLID

Single responsibility: growable-text-box styling, nothing about validation
or what the text means. Open/closed: the auto-grow mechanism is a CSS
property, not a prop this component exposes — a future need to disable
auto-grow is a `style` override at the call site, not a new boolean here.
