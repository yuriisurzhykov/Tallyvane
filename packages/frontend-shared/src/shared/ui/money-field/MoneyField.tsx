"use client";

import type { NumberField as BaseNumberField } from "@base-ui/react/number-field";
import { NumberField, type NumberFieldRootProps } from "../number-field";

/* ------------------------------------------------------------------------ */
/* Root                                                                      */

/* ------------------------------------------------------------------------ */

export interface MoneyFieldRootOwnProps {
    /** ISO 4217 currency code, passed straight to `Intl.NumberFormat`. @default "USD" — no other currency is used anywhere in this codebase yet (YAGNI). */
    readonly currency?: string;
    /** Minimum value, in cents. */
    readonly min?: number;
    /** Maximum value, in cents. */
    readonly max?: number;
    /** Step for increment/decrement/scrub, in cents. @default 100 (one dollar) — mirrors `NumberField`'s own default of stepping by 1 of the displayed unit. */
    readonly step?: number;
    /** Fires after the value is committed (blur, or pointer release after scrubbing/stepping) — see `NumberField.Root`'s own `onValueCommitted`. Cents, like every other value on this boundary. */
    readonly onValueCommitted?: ((value: number | null, eventDetails: BaseNumberField.Root.CommitEventDetails) => void) | undefined;
}

/**
 * Controlled/uncontrolled as a discriminated union (`SKILL.md` §3.4) — both cents.
 * `NumberField.tsx` itself skips this (it is a bare re-export of Base UI's own loose
 * `value?`/`defaultValue?` pair, both independently optional); `MoneyField.Root` does not
 * skip it because, unlike that bare re-export, it performs a real value transformation of
 * its own (cents ↔ dollars) and deserves a crisp typed contract at that boundary.
 */
export type MoneyFieldControlled = {
    readonly value: number | null;
    readonly onValueChange: (value: number | null, eventDetails: BaseNumberField.Root.ChangeEventDetails) => void;
    readonly defaultValue?: never;
};

export type MoneyFieldUncontrolled = {
    readonly defaultValue?: number;
    readonly value?: never;
    readonly onValueChange?: never;
};

export type MoneyFieldRootProps = MoneyFieldRootOwnProps &
    (MoneyFieldControlled | MoneyFieldUncontrolled) &
    Omit<NumberFieldRootProps, "value" | "defaultValue" | "onValueChange" | "onValueCommitted" | "min" | "max" | "step" | "format">;

/**
 * Cents ↔ dollars at this component's own boundary — see this component's own README for why
 * `NumberField.Root`'s `format` prop (a plain `Intl.NumberFormatOptions` passthrough, verified
 * against its own `.d.ts`) cannot do this by itself: it only changes how the *same* numeric
 * value is displayed, so feeding it raw cents would format `4250` as literally "$4,250.00",
 * not "$42.50". `Math.round`, not a bare multiply, on the return trip — `dollars * 100` alone
 * reintroduces the float noise ("never a float" is this component's entire reason to exist).
 */
function centsToDollars(cents: number): number {
    return cents / 100;
}

function dollarsToCents(dollars: number | null): number | null {
    return dollars === null ? null : Math.round(dollars * 100);
}

/**
 * The only part of this module that knows about cents at all — `Group`/`Input`/`Increment`/
 * `Decrement`/`ScrubArea`/`ScrubAreaCursor` below are `NumberField`'s own parts, reused
 * unmodified, because none of them touch value semantics: they render UI reading from
 * `NumberField.Root`'s own context, which the real `NumberField.Root` this component renders
 * internally still provides transparently. Tracks the *displayed* dollar amount as what
 * `NumberField.Root`'s own `value`/`onValueChange` operate on, converting to/from the stored
 * integer cents only here, at the public boundary — verified empirically, not assumed, by
 * `MoneyField.test.tsx` asserting the actual emitted `onValueChange` value after a real
 * rendered interaction.
 */
function Root({
                  currency = "USD",
                  min,
                  max,
                  step = 100,
                  value,
                  defaultValue,
                  onValueChange,
                  onValueCommitted,
                  ...rest
              }: MoneyFieldRootProps) {
    const format: Intl.NumberFormatOptions = { style: "currency", currency };

    return (
        <NumberField.Root
            { ...rest }
            format={ format }
            step={ centsToDollars(step) }
            { ...(min !== undefined ? { min: centsToDollars(min) } : {}) }
            { ...(max !== undefined ? { max: centsToDollars(max) } : {}) }
            { ...(value !== undefined ? { value: value === null ? null : centsToDollars(value) } : {}) }
            { ...(defaultValue !== undefined ? { defaultValue: centsToDollars(defaultValue) } : {}) }
            { ...(onValueChange
                ? { onValueChange: (dollars: number | null, eventDetails: BaseNumberField.Root.ChangeEventDetails) => onValueChange(dollarsToCents(dollars), eventDetails) }
                : {}) }
            { ...(onValueCommitted
                ? {
                    onValueCommitted: (dollars: number | null, eventDetails: BaseNumberField.Root.CommitEventDetails) =>
                        onValueCommitted(dollarsToCents(dollars), eventDetails),
                }
                : {}) }
        />
    );
}

/* ------------------------------------------------------------------------ */

/**
 * Tier 0 — a currency-formatted number field whose public value is always integer cents, per
 * `COMPONENTS.md`. Composes `NumberField` (Tier 0 composing Tier 0, `COMPONENTS.md` §2): `Root`
 * is a real wrapper performing the cents/dollars conversion documented above; every other part
 * is `NumberField`'s own, reused unmodified, since none of them carry money-specific knowledge.
 */
export const MoneyField = {
    Root,
    Label: NumberField.Label,
    Group: NumberField.Group,
    Input: NumberField.Input,
    Increment: NumberField.Increment,
    Decrement: NumberField.Decrement,
    ScrubArea: NumberField.ScrubArea,
    ScrubAreaCursor: NumberField.ScrubAreaCursor,
};
