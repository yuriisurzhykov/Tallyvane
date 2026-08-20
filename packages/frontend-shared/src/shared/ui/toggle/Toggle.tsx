import type { ReactNode } from "react";
import { Toggle as BaseToggle } from "@base-ui/react/toggle";
import type { ToggleProps as BaseToggleProps } from "@base-ui/react/toggle";

export interface ToggleOwnProps {
    readonly children: ReactNode;
    readonly className?: string;
}

/**
 * `pressed`/`defaultPressed`/`onPressedChange` are Base UI's own
 * controlled/uncontrolled trio for this component (verified against
 * `@base-ui/react/toggle`'s `Toggle.d.ts`) — delegated entirely, never
 * reimplemented here. `value` is the id this toggle answers to inside a
 * `ToggleGroup`; omitted, a `Toggle` still works standalone as a plain
 * two-state button.
 */
export type ToggleProps<Value extends string = string> = ToggleOwnProps & Omit<BaseToggleProps<Value>, "children" | "className">;

const CLASS_NAME =
    "inline-flex items-center justify-center gap-inline-tight rounded-control border border-border-default px-stack py-inline text-body-strong text-text-primary transition-hover hover:bg-surface-row-hover focus-visible:focus-ring data-[pressed]:bg-interactive-primary-subtle data-[pressed]:text-interactive-primary-text data-[disabled]:opacity-60 data-[disabled]:cursor-not-allowed";

/**
 * Tier 0 — thin styling wrapper over Base UI's `Toggle` (ADR-031): pressed
 * state, keyboard activation and disabled semantics all come from
 * `@base-ui/react/toggle`, which emits `data-pressed`/`data-disabled`
 * (`ToggleDataAttributes`) rather than classes — this component only maps
 * those attributes onto tokens.
 */
export function Toggle<Value extends string = string>({ children, className, ...rest }: ToggleProps<Value>) {
    return (
        <BaseToggle className={[CLASS_NAME, className].filter(Boolean).join(" ")} {...rest}>
            {children}
        </BaseToggle>
    );
}
