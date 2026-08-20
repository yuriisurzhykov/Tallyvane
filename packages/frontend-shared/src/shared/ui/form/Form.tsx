import type { ReactNode } from "react";
import { Form as BaseForm, type FormProps as BaseFormProps } from "@base-ui/react/form";

export interface FormOwnProps {
    readonly children: ReactNode;
    /** Layout and position only — see `COMPONENTS.md` §11. */
    readonly className?: string;
}

/**
 * `errors`/`onFormSubmit`/`actionsRef`/`validationMode` are Base UI's own
 * props for this component (verified against `@base-ui/react/form`'s
 * `Form.d.ts`), delegated entirely rather than reimplemented — `errors` in
 * particular is the real prop name for server-side field errors, not
 * assumed. `FormValues` stays generic, like `ToggleGroup`'s own `Value` type
 * parameter, since a caller submitting a typed set of fields shouldn't have
 * to widen it to a bag type just to use this wrapper. Bounded by
 * `Record<string, unknown>` rather than Base UI's own `Record<string, any>`
 * default — `no-explicit-any` is a workspace-wide rule, and `unknown` is a
 * strict subtype of `any` here, so it satisfies Base UI's own constraint
 * without reintroducing the type hole into this component's public surface.
 */
export type FormProps<FormValues extends Record<string, unknown> = Record<string, unknown>> = FormOwnProps &
    Omit<BaseFormProps<FormValues>, "children" | "className">;

const CLASS_NAME = "flex flex-col gap-stack";

/**
 * Tier 0 — consolidated submission and server-error mapping, per
 * `COMPONENTS.md`. A thin wrapper over Base UI's `Form` (ADR-031): native
 * submission handling, blocking submit on an invalid field, focusing the
 * first invalid control, and holding server-side field errors all come from
 * `@base-ui/react/form` — this component supplies only the default
 * field-to-field layout (`gap-stack`, the role documented for spacing
 * between elements inside a card) and this package's own token discipline.
 *
 * This package's own `Field` does not read `errors` automatically, though —
 * its `error` prop is what actually decides what `Field.Error` shows (see
 * `Field.tsx`), by design: a Tier 0 component holds no more knowledge than
 * the tokens it applies, and "which key in `errors` belongs to this field"
 * is exactly the kind of caller-specific knowledge it must not carry. A real
 * call site reads `errors[name]` itself and passes it into `Field`'s own
 * `error` prop — the pattern `Form.test.tsx`'s integration spec exercises.
 */
export function Form<FormValues extends Record<string, unknown> = Record<string, unknown>>({
    className,
    ...props
}: FormProps<FormValues>) {
    return <BaseForm className={[CLASS_NAME, className].filter(Boolean).join(" ")} {...props} />;
}
