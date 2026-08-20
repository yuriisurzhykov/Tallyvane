import type { ReactElement } from "react";
import { Field as BaseField } from "@base-ui/react/field";
import { Text } from "../text";

export interface FieldProps {
    readonly label: string;
    readonly description?: string;
    readonly error?: string;
    readonly required?: boolean;
    /**
     * The actual control — a plain `<input>` today, eventually `Input` /
     * `Select` / etc. Typed as `ReactElement`, not the wider `ReactNode`:
     * it is threaded through `Field.Control`'s `render` prop, which needs
     * exactly one element to attach Base UI's field wiring to (label
     * association, `aria-invalid`, `aria-describedby`). A string, fragment
     * or multiple children would fail this contract, so the type says so
     * rather than deferring to a runtime throw.
     */
    readonly children: ReactElement;
}

/**
 * Thin styling wrapper over Base UI's Field primitive (ADR-031): every ARIA
 * relationship — label-to-control association, `aria-describedby` for the
 * description or error, `aria-invalid` on the control — comes from
 * `@base-ui/react/field`. This component only supplies tokens and a smaller
 * public prop surface on top of it.
 *
 * Description and error are mutually exclusive in the rendered output —
 * error takes priority when both are passed.
 */
export function Field({ label, description, error, required = false, children }: FieldProps) {
    const hasError = Boolean(error);

    return (
        <BaseField.Root invalid={hasError} className="flex flex-col gap-inline-tight">
            <Text variant="small" color="primary" render={<BaseField.Label />}>
                {label}
            </Text>
            <BaseField.Control required={required} render={children} />
            {hasError ? (
                <Text variant="caption" tone="danger" render={<BaseField.Error match />}>
                    {error}
                </Text>
            ) : description ? (
                <Text variant="caption" color="muted" render={<BaseField.Description />}>
                    {description}
                </Text>
            ) : null}
        </BaseField.Root>
    );
}
