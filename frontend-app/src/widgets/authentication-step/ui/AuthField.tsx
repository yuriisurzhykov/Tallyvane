import type { ReactElement } from "react";
import { cloneElement } from "react";
import { Field } from "frontend-shared/ui/field";
import type { InputProps } from "frontend-shared/ui/input";

export interface AuthFieldProps {
    name: string;
    label: string;
    control: ReactElement<InputProps>;
    errors: Record<string, string>;
    help?: string;
}

export function AuthField({ name, label, control, errors, help }: AuthFieldProps) {
    const error = errors[name];
    const child = cloneElement(control, { name, id: `auth-${name}` });

    return (
        <Field label={label} {...(help ? { description: help } : {})} {...(error ? { error } : {})}
               required={Boolean(control.props.required)}>
            {child}
        </Field>
    );
}
