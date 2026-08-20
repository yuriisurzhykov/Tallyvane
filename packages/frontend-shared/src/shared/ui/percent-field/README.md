# percent-field

Displays a percentage, stores basis points. The 401(k) rate is `0`–`10000`,
not `0`–`100`, per `COMPONENTS.md`. Tier 0, composing `NumberField` (also
Tier 0) — the sibling of `money-field`, sharing its shape but not, it turns
out, its exact conversion math.

## What needed doing

Every rate in this product that reads as a percentage to a person — a 401(k)
match, an equity vest fraction, a probability-of-offer estimate — needs the
same integer-safety guarantee `MoneyField` gives dollar amounts, for the same
reason: a percentage compounding float noise across a chain of calculations
(gross pay × match rate × vest fraction) accumulates real, visible error.
Basis points (hundredths of a percentage point) are the standard fixed-point
representation for exactly this, the same way cents are for currency.

## The non-obvious fact this component rests on — different from

`MoneyField`'s, verified separately rather than assumed to match

`MoneyField`'s own README establishes that `NumberField.Root`'s `format` prop
only changes *display*, never *scales* the underlying value — feeding it raw
cents formats "four thousand two hundred fifty dollars," not `$42.50`. The
natural next assumption is that the same is true for `style: "percent"`: that
a raw basis-points integer would need converting to a "human percent number"
(basis points ÷ 100 = `2.5` for `2.5%`) before formatting, the same one-step
division `MoneyField` does for dollars. **That assumption is wrong, and
verifying it — rather than pattern-matching off `MoneyField`'s own shape — is
the entire point of this section.**

`Intl.NumberFormat`'s own `style: "percent"` does not treat its input as a
"percent number" at all. It treats it as a **ratio**, and multiplies by 100
only when *formatting*:

```
> new Intl.NumberFormat('en-US', { style: 'percent' }).format(0.5)
'50%'
> new Intl.NumberFormat('en-US', { style: 'percent' }).format(2.5)
'250%'
```

This is standard `Intl.NumberFormat` behavior, not something Base UI added —
but it changes what "the displayed value" means for this component
specifically. Reading `number-field/utils/parse.js` directly (not the
`.d.ts`, which says nothing about this — the behavior lives entirely in the
parsing implementation) confirms the *parsing* direction mirrors it exactly:

```js
const hasPercentSymbol = PERCENT_RE.test(formattedNumber) || style === 'percent';
// ...
} else if (!isUnitPercent && hasPercentSymbol) {
  num = shiftDecimal(num, -2);
}
```

Whenever `options.style === 'percent'`, Base UI's own `parseNumber` divides
the typed number by 100 (`shiftDecimal(num, -2)`) before treating it as the
field's real value — regardless of whether the user actually typed a literal
`%` character. So the value `NumberField.Root` genuinely operates on
internally, for a percent-formatted field, is **the ratio** (`0.025` for
`2.5%`), not `2.5`. The correct conversion chain is therefore:

```
basis points (public) → ratio = basisPoints / 10000 → NumberField.Root's own value
NumberField.Root's own value → basisPoints = Math.round(ratio * 10000) → basis points (public)
```

— one division by `10000` directly from basis points to ratio (skipping the
intermediate "percent number" entirely), not the two-step
"basis points → percent number → ratio" a naive reading might produce. Both
directions were checked live in Node before writing a line of component
code, and the full round trip is proven again in `PercentField.test.tsx` with
a real rendered interaction: typing `"2.5"` into a field with a `0` basis-point
default asserts the public `onValueChange` receives exactly `250`, not `2500`
or `25000`.

### The default fraction-digit count is a second, independent gotcha

`Intl.NumberFormat`'s own default for `style: "percent"` is **zero** fraction
digits — confirmed live: `format(0.0234)` (with no explicit digit options)
renders `"2%"`, silently rounding away real basis points a caller typed.
Basis-point precision needs up to two decimal percent digits (`12.34%`), so
this component's own `format` constant sets `minimumFractionDigits: 0,
maximumFractionDigits: 2` explicitly — `minimumFractionDigits: 0` keeps a
whole value reading as `"5%"` rather than a stiff `"5.00%"`, and
`maximumFractionDigits: 2` stops it from dropping data. `MoneyField` needed no
equivalent fix: `Intl.NumberFormat`'s own default for `style: "currency"`
already matches the currency's minor unit (2 digits for USD) with no
configuration at all — confirmed live in that component's own README — so
this is a genuine, `percent`-specific gotcha, not a copy-paste omission.

## What was actually done

`Root` is a real wrapping component, the only part of this module that knows
basis points exist. It builds one module-level `format` constant (percent
style needs no per-call configuration the way `MoneyField`'s `currency` prop
does — no known call site needs a locale-specific percent variant yet,
YAGNI), converts `min`/`max`/`step`/`value`/`defaultValue` from basis points
to the ratio documented above before handing them to the real
`NumberField.Root` it renders, and converts that Root's own
`onValueChange`/`onValueCommitted` (ratios) back to basis points before
calling the caller's. `Group`/`Input`/`Increment`/`Decrement`/`ScrubArea`/
`ScrubAreaCursor` are `NumberField`'s own exported parts, reused unmodified —
identical reasoning to `MoneyField.tsx`'s own, not repeated in full here.

### No hard `max`, on purpose — checked against the one example `COMPONENTS.md` actually gives

`COMPONENTS.md`'s own row for this component reads: "the 401(k) rate is
`0`–`10000`, not `0`–`100`" — read carefully, that sentence is about the
*basis-point encoding* (10000 basis points **is** 100%, the numerator, not a
hard ceiling), not a claim that every percent this component will ever hold
is capped at 100%. A real 401(k) plan can match more than 100% of a
contribution in some structures, and an equity vest tracked as a percentage
of a total grant is a comparable case. Baking a `max={10000}` into a
general-purpose Tier 0 primitive on the strength of one example call site
would be exactly the "abstracting on one example is a guess, not foresight"
mistake `SKILL.md`'s Rule of Three section warns against — so `min`/`max`
default to fully open, and a specific call site (a probability-of-offer
field, say, which genuinely cannot exceed 100%) supplies its own `max={10000}`
at that call site instead.

### `step` defaults to 100 basis points (one percentage point)

Mirrors `MoneyField`'s own reasoning for defaulting to one dollar: a caller
who never thinks about `step` gets "click moves by one visible unit,"
matching `NumberField`'s own upstream default of stepping by `1` of whatever
is displayed. `smallStep`/`largeStep` are left unexposed for the same YAGNI
reason as `MoneyField`'s: Base UI's own defaults (`0.1`/`10`, applied to the
*ratio*) already move `0.01` and `1` percentage points respectively with zero
conversion code needed to make that sensible.

## The same testing gotcha `MoneyField.test.tsx` already documents, confirmed to apply here too

This machine's runtime locale is `ru-UA`, not `en-US` (verified directly, not
assumed — see `money-field/README.md`'s identical entry for the full story).
`Intl.NumberFormat`'s own percent formatting under that locale renders
`"2,5 %"` for a value that reads as `"2.5%"` under `en-US` — comma decimal
separator, a space before the sign. Every test in `PercentField.test.tsx`
pins `locale="en-US"` explicitly for exactly this reason.

## SOLID

Single responsibility: the basis-points/ratio conversion at exactly one
boundary, nothing about what a percentage represents — a 401(k) match, a
vest fraction and an offer probability are all just `PercentField` underneath.
Open/closed: every visual/interaction concern is extended by `NumberField`'s
own evolution, never a new branch here. Dependency inversion: parsing,
formatting, clamping and interaction are `NumberField`'s (and Base UI's);
this file supplies only the value-space conversion and the fraction-digit
correction documented above.
