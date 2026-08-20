import type { ReactNode } from "react";
import { Fieldset as BaseFieldset } from "@base-ui/react/fieldset";
import { Text } from "../text";

export interface FieldsetProps {
    readonly legend: string;
    readonly children: ReactNode;
}

/**
 * Thin styling wrapper over Base UI's Fieldset primitive (ADR-031): grouping
 * and the legend-to-fieldset accessible-name wiring come from
 * `@base-ui/react/fieldset`, this component only supplies tokens.
 *
 * `Fieldset.Legend` renders a `<div>` labelling the group via
 * `aria-labelledby`, not a native `<legend>` — see this batch's authoring
 * report for why that default was kept rather than forced with `render`.
 */
export function Fieldset({ legend, children }: FieldsetProps) {
    return (
        <BaseFieldset.Root className="flex flex-col gap-stack">
            <Text variant="title3" color="primary" render={<BaseFieldset.Legend />}>
                {legend}
            </Text>
            {children}
        </BaseFieldset.Root>
    );
}
