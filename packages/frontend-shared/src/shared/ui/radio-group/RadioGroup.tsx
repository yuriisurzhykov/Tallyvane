import type { ReactNode } from "react";
import type { RadioGroupProps as BaseRadioGroupProps } from "@base-ui/react/radio-group";
import { RadioGroup as BaseRadioGroup } from "@base-ui/react/radio-group";

export interface RadioGroupOwnProps {
    /** The `Radio`s this group coordinates. */
    readonly children: ReactNode;
    readonly className?: string;
}

/**
 * `value`/`defaultValue`/`onValueChange` are Base UI's own
 * controlled/uncontrolled trio, verified against
 * `@base-ui/react/radio-group`'s `RadioGroup.d.ts`. Generic over `Value`
 * the same way `Radio` is, defaulting to `string` for the common case
 * (work mode, seniority — an enumerated set of string options) while
 * staying open to a caller instantiating `RadioGroup<number>` directly.
 */
export type RadioGroupProps<Value = string> =
    RadioGroupOwnProps
    & Omit<BaseRadioGroupProps<Value>, "children" | "className">;

/**
 * Vertical by default, same reasoning as `CheckboxGroup`'s own: a radio
 * group is conventionally a list of mutually exclusive options read
 * top-to-bottom, unlike `ToggleGroup`'s inline row of view-switcher
 * buttons. Base UI's `RadioGroup.d.ts` carries no `orientation` prop to
 * delegate to either. `className` stays open for a caller that genuinely
 * wants a horizontal layout.
 */
const CLASS_NAME = "flex flex-col gap-stack-tight";

/**
 * Tier 0 — thin styling wrapper over Base UI's `RadioGroup` (ADR-031): the
 * shared selected-value state and the arrow-key roving-tabindex navigation
 * between radios (Base UI's own `CompositeRoot` internals, confirmed by
 * reading `RadioGroup.js` rather than assumed from the `.d.ts` alone —
 * this is real JS keyboard handling, not reliance on native browser
 * same-`name` radio grouping) both come from Base UI. This component
 * supplies only the list layout and gap token.
 */
export function RadioGroup<Value = string>({ children, className, ...rest }: RadioGroupProps<Value>) {
    return (
        <BaseRadioGroup className={ [CLASS_NAME, className].filter(Boolean).join(" ") } { ...rest }>
            { children }
        </BaseRadioGroup>
    );
}
