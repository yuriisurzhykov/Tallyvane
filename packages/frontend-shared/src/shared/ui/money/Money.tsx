import { Numeric } from "../numeric";

export interface MoneyProps {
    /** Integer cents — never a float, per `COMPONENTS.md`'s "never a float" rule for every money value in this codebase. */
    readonly cents: number;
    /** ISO 4217 currency code, passed straight to `Intl.NumberFormat`. @default "USD" — mirrors `MoneyField`'s own default; no other currency is used anywhere in this codebase yet (YAGNI). */
    readonly currency?: string;
    /** Layout and position only — see `COMPONENTS.md` §11. Forwarded to `Numeric`. */
    readonly className?: string;
}

/**
 * Cents → display string, the one-way half of the conversion
 * `MoneyField.tsx`'s own `centsToDollars` performs at its display boundary
 * — mirrored here rather than reinvented, per this component's own README.
 * `Money` never writes a value back, so it never needs `dollarsToCents`'s
 * `Math.round` counterpart: `Intl.NumberFormat`'s currency style already
 * rounds to two decimal places on the way out, and nothing downstream of
 * this string is ever parsed back into a number.
 *
 * No `locale` argument — deliberately unspecified, not overlooked. This
 * lets the runtime's own default locale resolve the thousands separator,
 * decimal mark and symbol placement, the same way `NumberField.Root` does
 * when no caller passes its own `locale` prop. `Money.test.tsx` computes
 * its expected strings with this identical call rather than a hardcoded
 * `"$42.50"`, precisely because this machine's own default locale is
 * `ru-UA`, not `en-US` — the exact gotcha `money-field/README.md` already
 * documents for `MoneyField`, avoided here rather than repeated.
 */
function formatCents(cents: number, currency: string): string {
    return new Intl.NumberFormat(undefined, { style: "currency", currency }).format(cents / 100);
}

/**
 * Tier 1 — composes `Numeric` (Tier 0) for the tabular-figure typography;
 * this component's own and only job is deciding the formatted string.
 * A component rather than a `formatCents` helper so the tabular-figure
 * rule cannot be forgotten at a call site that reaches for a helper
 * instead (`COMPONENTS.md` §4).
 */
export function Money({ cents, currency = "USD", className }: MoneyProps) {
    return <Numeric {...(className ? { className } : {})}>{formatCents(cents, currency)}</Numeric>;
}
