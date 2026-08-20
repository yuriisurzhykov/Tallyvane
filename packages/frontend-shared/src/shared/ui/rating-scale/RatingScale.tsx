import { useState } from "react";
import type { RadioGroupChangeEventDetails } from "@base-ui/react/radio-group";
import { RadioGroup as BaseRadioGroup } from "@base-ui/react/radio-group";
import { Radio as BaseRadio } from "@base-ui/react/radio";

export type RatingValue = 1 | 2 | 3 | 4 | 5;

const RATING_VALUES: readonly RatingValue[] = [1, 2, 3, 4, 5];

/**
 * `0` is Base UI's own value for "no dot currently matches", never a real
 * rating (`RatingValue` starts at 1) — see this component's own comment on
 * `toInternalValue` for why the public API needs this translation at all.
 */
type InternalValue = RatingValue | 0;

function toInternalValue(value: RatingValue | undefined): InternalValue {
    return value ?? 0;
}

function toPublicValue(value: InternalValue): RatingValue | undefined {
    return value === 0 ? undefined : value;
}

interface RatingScaleBaseProps {
    /** Accessible name for the whole group of five dots (e.g. "Interest level") — a group of unlabeled radios is not a valid control. No ambient default, same reasoning as `IconButton`'s required `label`. */
    readonly label: string;
    /**
     * Accessible name for one dot, e.g. `(value) => \`${value} of 5\``. This
     * component holds no copy of its own (`COMPONENTS.md` §12, "copy arrives
     * as props below Tier 3") — the exact wording, and its localisation, is
     * the caller's, mirroring Base UI's own `Slider.Thumb`
     * `getAriaValueText` callback-prop shape rather than inventing a new one.
     */
    readonly getValueLabel: (value: RatingValue) => string;
    readonly disabled?: boolean;
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

type RatingScaleControlledProps = RatingScaleBaseProps & {
    readonly value: RatingValue | undefined;
    readonly onValueChange: (value: RatingValue | undefined, eventDetails: RadioGroupChangeEventDetails) => void;
    readonly defaultValue?: never;
};

type RatingScaleUncontrolledProps = RatingScaleBaseProps & {
    readonly defaultValue?: RatingValue;
    readonly value?: never;
    readonly onValueChange?: never;
};

export type RatingScaleProps = RatingScaleControlledProps | RatingScaleUncontrolledProps;

/**
 * Same box size as `Checkbox`'s/`Radio`'s own — see `Checkbox.tsx`'s
 * comment on why this is a named constant rather than a token, and why
 * each file keeps its own copy.
 */
const DOT_SIZE = "1.25rem";

const DOT_CLASS_NAME =
    "inline-flex shrink-0 rounded-pill border-2 border-border-strong transition-hover hover:border-interactive-primary focus-visible:focus-ring data-[disabled]:cursor-not-allowed data-[disabled]:opacity-60";

/**
 * A dot's filled state is computed here, not read off Base UI's own
 * `data-checked` — a corrected wrong turn, not the original design: the
 * first version left this un-cumulative (only the exact selected dot
 * filled, matching a printed Likert scale), reasoning that this reads
 * differently from a public star-rating widget's "fill up to N"
 * convention. Live review disagreed — a row of dots where only the last one
 * fills reads as broken rather than deliberate, and "fill every dot up to
 * and including the selected one" is what a rating scale is expected to do
 * regardless of the underlying five-point-Likert framing. `data-[checked]`
 * only ever marks the one radio whose value equals the group's current
 * value, which cannot express "and everything before it," so this class is
 * applied directly from a value comparison instead.
 */
const DOT_FILLED_CLASS_NAME = "border-interactive-primary bg-interactive-primary";

const GROUP_CLASS_NAME = "inline-flex gap-inline-tight";

/**
 * Tier 0 — a row of five toggleable dots, visually related to `Dot` (a
 * solid filled circle carrying meaning) rather than to digits or stars.
 * Selecting a dot fills it and every dot before it — see `DOT_FILLED_CLASS_NAME`'s
 * own comment for why this replaced the original single-dot-only fill.
 *
 * Wraps `@base-ui/react/radio-group` and `@base-ui/react/radio` directly
 * rather than composing this same batch's own `Radio`/`RadioGroup`: the
 * two need incompatible visual treatments (a two-part ring-plus-inner-dot
 * for `Radio`, a single dot that is either a ring or a solid fill here),
 * and forcing a shared visual slot into `Radio` for one very
 * differently-shaped consumer would be over-engineering for two known call
 * sites (YAGNI/KISS — `.cursor/skills/component-authoring/SKILL.md` §4).
 * Reusing Base UI's own primitives directly still gets the real behaviour
 * — arrow-key roving focus between the five dots, Space to select — for
 * free, the same as `Radio.tsx` does.
 *
 * `value`/`onValueChange` never see a literal `undefined` at the Base UI
 * boundary, even though the public API's `RatingValue | undefined`
 * genuinely allows "not yet rated": Base UI's `useControlled` (confirmed
 * by reading `@base-ui/utils/useControlled.js` directly) decides whether a
 * component is controlled ONLY on its first render, by checking
 * `value !== undefined` — a real, universal React footgun (the same
 * `<input value={undefined}>` limitation React's own docs describe), not
 * a workaround for a bug. Passing `value={undefined}` on the first render
 * to start a genuinely controlled, currently-unrated scale would silently
 * and permanently lock this component into uncontrolled mode, so every
 * later `value` update from the caller would be ignored with no error.
 * `0` (never a real `RatingValue`, which starts at 1) is used as the
 * internal "nothing selected" sentinel instead, keeping `value !== undefined`
 * true from the very first render regardless of whether the caller's
 * current rating is set — `toInternalValue`/`toPublicValue` are the only
 * two places this translation happens.
 */
export function RatingScale({ label, getValueLabel, disabled, className, ...rest }: RatingScaleProps) {
    const groupClassName = [GROUP_CLASS_NAME, className].filter(Boolean).join(" ");

    // Narrows on `onValueChange`, not `"value" in rest`: both union members
    // declare a `value` key (`never`-typed but still present on the
    // uncontrolled side), so an `in` check does not actually discriminate
    // between them — confirmed by `tsc` itself still reporting
    // `onValueChange` as possibly `undefined` after switching to an inline
    // `"value" in rest` check. A `typeof` check on the one field that is a
    // real function on exactly one side narrows correctly.
    const isControlled = typeof rest.onValueChange === "function";

    /**
     * Tracked locally, in both modes, purely to compute cumulative fill —
     * not a second source of truth for the actual selection, which stays
     * exactly where it already was (Base UI's own controlled/uncontrolled
     * state, per the class comment above). In controlled mode this mirrors
     * the caller's own `value` on every render, so it is never read in that
     * branch; in uncontrolled mode it is the only place this component
     * knows the current value at all, updated from the same
     * `onValueChange` Base UI already calls for real selection.
     */
    const [uncontrolledValue, setUncontrolledValue] = useState<InternalValue>(() =>
        isControlled ? 0 : toInternalValue(rest.defaultValue),
    );
    const currentValue = isControlled ? toInternalValue(rest.value) : uncontrolledValue;

    const dots = RATING_VALUES.map((value) => (
        <BaseRadio.Root
            key={ value }
            value={ value }
            aria-label={ getValueLabel(value) }
            className={ [DOT_CLASS_NAME, value <= currentValue ? DOT_FILLED_CLASS_NAME : ""].filter(Boolean).join(" ") }
            style={ { width: DOT_SIZE, height: DOT_SIZE } }
        />
    ));

    if (isControlled) {
        const { value, onValueChange } = rest;
        return (
            <BaseRadioGroup<InternalValue>
                aria-label={ label }
                disabled={ disabled }
                className={ groupClassName }
                value={ toInternalValue(value) }
                onValueChange={ (next, eventDetails) => onValueChange(toPublicValue(next), eventDetails) }
            >
                { dots }
            </BaseRadioGroup>
        );
    }

    const { defaultValue } = rest;
    return (
        <BaseRadioGroup<InternalValue>
            aria-label={ label }
            disabled={ disabled }
            className={ groupClassName }
            defaultValue={ toInternalValue(defaultValue) }
            onValueChange={ (next) => setUncontrolledValue(next) }
        >
            { dots }
        </BaseRadioGroup>
    );
}
