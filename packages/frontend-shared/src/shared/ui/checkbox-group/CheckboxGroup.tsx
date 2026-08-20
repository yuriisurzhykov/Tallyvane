import type { ReactNode } from "react";
import type { CheckboxGroupProps as BaseCheckboxGroupProps } from "@base-ui/react/checkbox-group";
import { CheckboxGroup as BaseCheckboxGroup } from "@base-ui/react/checkbox-group";

export interface CheckboxGroupOwnProps {
    /** The `Checkbox`es this group coordinates. */
    readonly children: ReactNode;
    readonly className?: string;
}

/**
 * `value`/`defaultValue`/`onValueChange` (the ticked checkboxes' own
 * `value`s) and `allValues` (for a "select all" parent checkbox) are Base
 * UI's own vocabulary, verified against `@base-ui/react/checkbox-group`'s
 * `CheckboxGroup.d.ts`. A child `Checkbox` rendered inside this group reads
 * its own checked state from the group's shared context automatically —
 * this wrapper passes nothing down to it itself, the same division of
 * labour `ToggleGroup` already establishes for `Toggle`.
 */
export type CheckboxGroupProps = CheckboxGroupOwnProps & Omit<BaseCheckboxGroupProps, "children" | "className">;

/**
 * Vertical by default: unlike `ToggleGroup` (an inline set of view-switcher
 * buttons), a checkbox group is conventionally a list of options read
 * top-to-bottom. `className` stays open for a caller that genuinely wants a
 * horizontal layout (layout and position only, per `COMPONENTS.md` §11) —
 * no orientation prop of its own, since Base UI's `CheckboxGroup` has none
 * to delegate to (unlike `ToggleGroup`'s), and no known call site needs one
 * yet.
 */
const CLASS_NAME = "flex flex-col gap-stack-tight";

/**
 * Tier 0 — thin styling wrapper over Base UI's `CheckboxGroup` (ADR-031):
 * the shared ticked-values state, and the parent/child "select all"
 * wiring `useCheckboxGroupParent` provides, are both Base UI's. This
 * component supplies only the list layout and gap token.
 */
export function CheckboxGroup({ children, className, ...rest }: CheckboxGroupProps) {
    return (
        <BaseCheckboxGroup className={ [CLASS_NAME, className].filter(Boolean).join(" ") } { ...rest }>
            { children }
        </BaseCheckboxGroup>
    );
}
