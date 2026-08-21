# money-field

Cents in, cents out. **Never a float** — the API is integer cents throughout,
per `COMPONENTS.md`. Tier 0, composing `NumberField` (also Tier 0).

## What needed doing

Every dollar amount in this product — stated salary, a comp breakdown line
item, the weekly-goal target — needs a real numeric control that reads as
currency, while the value a caller actually stores and compares must stay an
integer. Floats and money don't mix: `0.1 + 0.2 !== 0.3` in IEEE 754, and a
comp calculation compounding that error across a dozen line items is a real
bug, not a rounding curiosity. Cents avoid the whole class of error, which is
exactly why `COMPONENTS.md` states the constraint as a hard rule rather than a
style preference.

## The one question this batch's brief called out as the most important thing to get right

Does `NumberField.Root`'s own `format` prop (a plain `Intl.NumberFormatOptions`
passthrough) let a caller keep `value` as the raw integer while only changing
*display* formatting — or does it only change how the *same* numeric value is
displayed? Verified against the installed `.d.ts` directly
(`format?: Intl.NumberFormatOptions | undefined` on `NumberFieldRootProps`,
nothing else) and empirically in Node:

```
> new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(42.5)
'$42.50'
> new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(4250)
'$4,250.00'
```

The second line is the answer: feeding `format` the raw cents integer `4250`
formats it as *four thousand two hundred fifty dollars*, not `$42.50`.
`format` only changes presentation of whatever number it's given — it has no
scale/divide option. So this component has to manage the display/stored
split itself: `MoneyField.Root` tracks the *displayed* value (dollars) as what
the `NumberField.Root` it renders internally actually operates on, and
converts to/from the *stored* integer (cents) only at `MoneyField`'s own
public `value`/`defaultValue`/`onValueChange`/`onValueCommitted` boundary.
`Math.round(dollars * 100)` on the way back to cents, not a bare multiply —
floating-point noise (`42.51 * 100` is not exactly `4251` in every case) is
precisely the failure mode "never a float" exists to rule out, so the
conversion itself has to be float-safe.

This was verified with a real rendered test, not assumed: `MoneyField.test.tsx`
types a dollar amount into the input and asserts the exact integer
`onValueChange` receives, and separately asserts the formatted display text —
proving the two are related by exactly this conversion, not by coincidence.

## What was actually done

`Root` is a real wrapping component (not a bare re-export, unlike
`NumberField.tsx`'s own `Root`) — the only part of this module that knows
cents exist at all. It builds `format={{ style: "currency", currency }}`
(`currency` defaults to `"USD"`; no other currency is used anywhere in this
codebase yet, so a full multi-currency surface — locale-aware symbol
placement per currency, a currency *picker*, etc. — is YAGNI until a real
call site asks), converts `min`/`max`/`step`/`value`/`defaultValue` from
cents to dollars before handing them to the real `NumberField.Root` it
renders, and converts `NumberField.Root`'s own `onValueChange`/
`onValueCommitted` callbacks (dollars) back to cents before calling the
caller's.

`Group`/`Input`/`Increment`/`Decrement`/`ScrubArea`/`ScrubAreaCursor` are
`NumberField`'s own exported parts, reused unmodified (Tier 0 composing Tier
0, `COMPONENTS.md` §2) — none of them touch value semantics at all; they
render UI reading from `NumberField.Root`'s own React context, which the real
`NumberField.Root` rendered inside `MoneyField.Root` still provides
transparently. Wrapping them in money-specific copies would be pure ceremony
for zero behavioral difference.

### Controlled/uncontrolled is a discriminated union here, even though `NumberField.tsx` itself isn't

`NumberField.tsx`'s own `Root` is a bare re-export of `BaseNumberField.Root`,
so it inherits Base UI's own loose typing — `value`/`defaultValue` both
independently optional, "don't mix them" left as an unenforced convention.
`MoneyField.Root` does not follow that precedent: unlike a bare re-export, it
performs a real value transformation of its own at this exact boundary, and
`SKILL.md` §3.4 asks for exactly this case — the controlled/uncontrolled
choice is a type error, not a runtime footgun, using `never` on the forbidden
fields of each union member. No new `useControllableValue`-style hook was
needed to get this: the actual state resolution (is this field controlled or
not, do we own local state) is left entirely to `NumberField.Root`'s own
Base UI internals — `MoneyField.Root` only ever forwards a converted
`value` **or** a converted `defaultValue`, never invents state of its own.

### Step, min, max: cents all the way through, with a default that mirrors `NumberField`'s own

`step` defaults to `100` (one dollar) — chosen because it mirrors
`NumberField`'s own upstream default of stepping by `1` of whatever unit is
displayed; a caller who never thinks about `step` at all gets the same
"click increments by one visible unit" behavior either component would give.
`min`/`max` have no default at all (open-ended), matching this component's
own general-purpose Tier 0 scope — a specific call site's own bound (e.g. a
salary field's realistic floor) belongs at that call site, not baked in here.
`smallStep`/`largeStep` (Base UI's own alt/shift-modified stepping) are
deliberately **not** exposed as separate cents-scaled props: no known call
site needs alt/shift step modulation yet (YAGNI, Rule of Three), and Base
UI's own defaults (`smallStep: 0.1`, `largeStep: 10`) already apply sensibly
to the *displayed dollar value* with zero conversion needed — alt+step moves
10¢, shift+step moves $10, both already sensible currency increments with no
code required to make them so.

## A real testing gotcha, found only by running a rendered test

This machine's runtime locale is `ru-UA`, not `en-US` — confirmed by running
`new Intl.NumberFormat().resolvedOptions().locale` directly in Node, not
assumed. Formatting `42.5` as USD currency under that default locale renders
`"42,50 $"` (comma decimal separator, symbol *after* the number), not the
`"$42.50"` a test author from an `en-US` mental model would expect. Every
test in `MoneyField.test.tsx` passes `locale="en-US"` explicitly for exactly
this reason — `NumberField.Root`'s own `locale` prop passes straight through
via this component's `...rest` spread, so nothing new had to be built to fix
this, only made explicit in the tests. Left undocumented, this is the kind of
gotcha that reads as "the component is broken" the moment CI runs on a
differently-configured machine.

## SOLID

Single responsibility: the cents/dollars conversion at exactly one boundary,
nothing about what a dollar amount actually represents — a salary, a
compensation line item and the weekly-goal target are all just `MoneyField`
underneath, per `entities/compensation`'s and `Stat`'s eventual composition
of it. Open/closed: every visual/interaction concern (the box, the stepper
buttons, scrubbing) is extended by `NumberField`'s own evolution, never by a
new branch inside this file. Dependency inversion: parsing, formatting,
clamping and all keyboard/pointer interaction are entirely `NumberField`'s
(and, beneath it, Base UI's); this file supplies only the value-space
conversion and the currency default.
