"use client";

import type { NumberField as BaseNumberField } from "@base-ui/react/number-field";
import { NumberField, type NumberFieldRootProps } from "../number-field";

/* ------------------------------------------------------------------------ */
/* Root                                                                      */

/* ------------------------------------------------------------------------ */

export interface PercentFieldRootOwnProps {
    /** Minimum value, in basis points. No default — see this component's own README on why a Tier 0 primitive must not bake in a `0`/`10000` bound a specific call site happens to want. */
    readonly min?: number;
    /** Maximum value, in basis points. Open-ended by default, for the same reason as `min` — a 401(k) match can exceed 100%. */
    readonly max?: number;
    /** Step for increment/decrement/scrub, in basis points. @default 100 (one percentage point) */
    readonly step?: number;
    /** Fires after the value is committed (blur, or pointer release after scrubbing/stepping) — see `NumberField.Root`'s own `onValueCommitted`. Basis points, like every other value on this boundary. */
    readonly onValueCommitted?: ((value: number | null, eventDetails: BaseNumberField.Root.CommitEventDetails) => void) | undefined;
}

/**
 * Controlled/uncontrolled as a discriminated union (`SKILL.md` §3.4) — both basis points. Same
 * reasoning as `MoneyField.Root`'s own identical choice: a real value transformation at this
 * boundary earns a crisp typed contract that `NumberField.tsx`'s own bare re-export doesn't need.
 */
export type PercentFieldControlled = {
    readonly value: number | null;
    readonly onValueChange: (value: number | null, eventDetails: BaseNumberField.Root.ChangeEventDetails) => void;
    readonly defaultValue?: never;
};

export type PercentFieldUncontrolled = {
    readonly defaultValue?: number;
    readonly value?: never;
    readonly onValueChange?: never;
};

export type PercentFieldRootProps = PercentFieldRootOwnProps &
    (PercentFieldControlled | PercentFieldUncontrolled) &
    Omit<NumberFieldRootProps, "value" | "defaultValue" | "onValueChange" | "onValueCommitted" | "min" | "max" | "step" | "format">;

/**
 * Basis points ↔ ratio, **not** basis points ↔ "human percent number" — this is the one
 * non-obvious fact this component rests on, verified by reading
 * `number-field/utils/parse.js` directly rather than assumed from `MoneyField`'s analogous
 * shape. `Intl.NumberFormat`'s own `style: "percent"` multiplies the *raw* value by 100 when
 * formatting (`format(0.025)` → `"2.5%"`, confirmed live in Node) and Base UI's own
 * `parseNumber` divides back by 100 on the way in whenever `options.style === 'percent'`
 * (`num = shiftDecimal(num, -2)` — read directly, not guessed). So the value
 * `NumberField.Root` actually operates on internally is the **ratio** (`0`–`1`, or beyond for
 * an over-100% rate), not a "2.5-for-2.5%" number the way `MoneyField`'s dollars are a
 * "42.50-for-$42.50" number. `Math.round` on the return trip to basis points for the same
 * float-safety reason `MoneyField`'s own conversion needs it.
 */
function basisPointsToRatio(basisPoints: number): number {
    return basisPoints / 10000;
}

function ratioToBasisPoints(ratio: number | null): number | null {
    return ratio === null ? null : Math.round(ratio * 10000);
}

/** `maximumFractionDigits: 2` is not decoration — `Intl.NumberFormat`'s own default for `style: "percent"` is `0` fraction digits, confirmed live (`format(0.0234)` → `"2%"`, silently dropping the real basis points a caller typed). Basis-point precision needs up to 2 decimal percent digits (`12.34%`); `minimumFractionDigits: 0` keeps a whole value reading as `"5%"`, not `"5.00%"`. */
const FORMAT: Intl.NumberFormatOptions = { style: "percent", minimumFractionDigits: 0, maximumFractionDigits: 2 };

/**
 * The only part of this module that knows about basis points at all — see `MoneyField.tsx`'s
 * identical reasoning for why `Group`/`Input`/`Increment`/`Decrement`/`ScrubArea`/
 * `ScrubAreaCursor` below are `NumberField`'s own parts, reused unmodified rather than
 * rewrapped. Converts basis points to/from the ratio `NumberField.Root` actually operates on —
 * verified empirically, not assumed, by `PercentField.test.tsx` typing a percent and asserting
 * the exact basis-point integer the public `onValueChange` receives.
 */
function Root({
                  min,
                  max,
                  step = 100,
                  value,
                  defaultValue,
                  onValueChange,
                  onValueCommitted,
                  ...rest
              }: PercentFieldRootProps) {
    return (
        <NumberField.Root
            { ...rest }
            format={ FORMAT }
            step={ basisPointsToRatio(step) }
            { ...(min !== undefined ? { min: basisPointsToRatio(min) } : {}) }
            { ...(max !== undefined ? { max: basisPointsToRatio(max) } : {}) }
            { ...(value !== undefined ? { value: value === null ? null : basisPointsToRatio(value) } : {}) }
            { ...(defaultValue !== undefined ? { defaultValue: basisPointsToRatio(defaultValue) } : {}) }
            { ...(onValueChange
                ? { onValueChange: (ratio: number | null, eventDetails: BaseNumberField.Root.ChangeEventDetails) => onValueChange(ratioToBasisPoints(ratio), eventDetails) }
                : {}) }
            { ...(onValueCommitted
                ? {
                    onValueCommitted: (ratio: number | null, eventDetails: BaseNumberField.Root.CommitEventDetails) =>
                        onValueCommitted(ratioToBasisPoints(ratio), eventDetails),
                }
                : {}) }
        />
    );
}

/* ------------------------------------------------------------------------ */

/**
 * Tier 0 — a percent-formatted number field whose public value is always integer basis points,
 * per `COMPONENTS.md`. Composes `NumberField` (Tier 0 composing Tier 0, `COMPONENTS.md` §2): `Root`
 * is a real wrapper performing the basis-points/ratio conversion documented above; every other
 * part is `NumberField`'s own, reused unmodified.
 */
export const PercentField = {
    Root,
    Group: NumberField.Group,
    Input: NumberField.Input,
    Increment: NumberField.Increment,
    Decrement: NumberField.Decrement,
    ScrubArea: NumberField.ScrubArea,
    ScrubAreaCursor: NumberField.ScrubAreaCursor,
};
