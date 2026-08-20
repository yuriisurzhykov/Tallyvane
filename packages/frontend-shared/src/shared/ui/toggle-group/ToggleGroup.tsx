import type { ReactNode } from "react";
import { ToggleGroup as BaseToggleGroup } from "@base-ui/react/toggle-group";
import type { ToggleGroupProps as BaseToggleGroupProps } from "@base-ui/react/toggle-group";

export interface ToggleGroupOwnProps {
    /** The `Toggle`s this group coordinates. */
    readonly children: ReactNode;
    readonly className?: string;
}

/**
 * `value`/`defaultValue`/`onValueChange` are Base UI's own
 * controlled/uncontrolled trio (an array of the pressed `Toggle` values).
 * `multiple` is its own name for single-select-vs-multi-select (verified
 * against `@base-ui/react/toggle-group`'s `ToggleGroup.d.ts`) — `false`
 * (the default) means exclusive choice, matching the "table-versus-board"
 * and "theme" use cases in `COMPONENTS.md`'s row for this pair.
 */
export type ToggleGroupProps<Value extends string = string> = ToggleGroupOwnProps &
    Omit<BaseToggleGroupProps<Value>, "children" | "className">;

const CLASS_NAME = "inline-flex gap-inline-tight data-[orientation=vertical]:flex-col";

/**
 * Tier 0 — thin styling wrapper over Base UI's `ToggleGroup` (ADR-031):
 * exclusive-vs-multi selection, roving focus and arrow-key navigation all
 * come from `@base-ui/react/toggle-group`. This component only lays the
 * row (or column) out and supplies the gap token; the `Toggle`s inside are
 * provided by the caller as children and pick up their own pressed
 * styling from the group's shared context automatically.
 */
export function ToggleGroup<Value extends string = string>({ children, className, ...rest }: ToggleGroupProps<Value>) {
    return (
        <BaseToggleGroup className={[CLASS_NAME, className].filter(Boolean).join(" ")} {...rest}>
            {children}
        </BaseToggleGroup>
    );
}
