# money

Formats integer cents — Tier 1, composing `Numeric` (Tier 0). Per
`COMPONENTS.md` §4: "a component rather than a helper so that the
tabular-figure rule cannot be forgotten."

## What needed doing

Every dollar amount that is only ever displayed, not edited — a job's stated
salary in a list row, a compensation line item on the brief, the weekly-goal
target on the Today header — needs the same two decisions made at every call
site: turn integer cents into a currency string, and give that string the
tabular-figure typography `Numeric` already owns. A plain `formatCents(cents,
currency)` helper would solve the first decision and silently drop the
second — nothing stops a call site from wrapping the result in a plain
`<span>` and losing the alignment the whole point of `Numeric` is to
guarantee. Making it a component instead of a function is the actual fix:
`<Money cents={cents} />` cannot render without the tabular-figure
typography, because the typography is what the component *is*.

## Why nothing existing could be reused instead

`MoneyField` (Tier 0) already owns a cents↔dollars conversion, but it is a
`NumberField.Root` wrapper built for editing — its `format` prop feeds
`Intl.NumberFormatOptions` into Base UI's own internal `Intl.NumberFormat`
call, which `Money` has no reason to depend on transitively just to read a
value it will never let anyone type into. `Numeric` itself deliberately does
not format numbers (its own README states the boundary explicitly) — it only
supplies typography for a string the caller already produced. Neither
existing component does the one thing `Money` actually needs: decide the
formatted string. `Money` is the missing piece between the two, not a
duplicate of either.

## What was actually done

`Money` calls `Intl.NumberFormat(undefined, { style: "currency", currency
}).format(cents / 100)` — the identical `{ style: "currency", currency }`
shape `MoneyField.tsx`'s own `Root` already builds, and the identical
division-by-100 `centsToDollars` already performs, mirrored rather than
reinvented per this batch's own brief. Unlike `MoneyField`, there is no
`dollarsToCents`/`Math.round` counterpart, because `Money` never writes a
value back — the boundary is one-way, and `Intl.NumberFormat`'s currency
style already rounds to two decimals on the way out.

**Judgment call: no `locale` prop.** `MoneyField.Root` forwards a `locale`
prop straight through to Base UI's `NumberField.Root`. `Money`'s brief fixed
its public API to `cents` and `currency` only, and no known call site needs
a different locale from whatever the runtime's own default resolves to — so
`Money` calls `Intl.NumberFormat` with `undefined` as the first argument,
same as `NumberField.Root` does whenever a caller omits its own `locale`
prop, letting the runtime's default locale decide thousands separators,
decimal marks and symbol placement. This is a real, not incidental, decision
(YAGNI over adding a prop nothing asks for yet), listed for review rather
than settled silently.

**A repeated gotcha, avoided rather than re-hit.** `money-field/README.md`
already documents that this development machine's own default `Intl` locale
is `ru-UA`, under which USD renders `"42,50 $"`, not `"$42.50"` — verified
again directly here (`node -e "console.log(new
Intl.NumberFormat().resolvedOptions().locale)"` → `ru-UA`). Since `Money` has
no `locale` prop to pin the way `MoneyField.test.tsx` pins one, `Money.test.tsx`
does not hardcode an assumed `"$42.50"` string at all — it computes each
expected value with the exact same `Intl.NumberFormat` call `Money.tsx` makes,
so the test passes identically regardless of which locale the machine or CI
runner happens to default to, while still proving the real conversion.

That computed-value approach hit a second, smaller version of the same class
of gotcha on the first run: under `ru-UA`, `Intl.NumberFormat` separates the
amount from the currency symbol with a real `U+00A0` non-breaking space
(`"42,50\u00A0$"`), not a plain space. `@testing-library/dom`'s default text
normalizer collapses that whitespace on the *rendered* DOM node before
comparing, but does not run the same normalization over a plain string
matcher — so the first version of this test failed even though the printed
diff in the terminal showed the two sides looking identical (a terminal
renders `\u00A0` and `" "` the same way). The fix is normalizing the
computed expected string with the same `.replace(/\s/g, " ")` the DOM side
effectively already gets, not hardcoding a literal or disabling
normalization on either side.

## Why it's understandable, scalable, extensible

Understandable: one call, one conversion, no branching — a reader sees the
whole component in four lines. Scalable: every money value in the product
(salary, compensation lines, the weekly-goal target) reaches the same
component, so a future change to money typography or formatting has exactly
one place to change. Extensible: a real per-user locale, if one is ever
needed, is a new optional prop threaded into the existing
`Intl.NumberFormat` call — the formatting call site does not move.

## SOLID

Single responsibility: deciding the formatted cents string, nothing about
typography (delegated to `Numeric`) and nothing about what the amount
represents (a salary versus a fee is a Tier 3+ concern). Open/closed: a
locale prop, if a real call site ever needs one, extends the existing
`Intl.NumberFormat` call with a new argument rather than adding branching.
Dependency inversion: `Money` depends on `Numeric`'s public `children`/
`className` contract, never on `Text`'s internal variant resolution that
`Numeric` itself owns.
