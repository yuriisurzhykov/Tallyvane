# form

Consolidated submission and server-error mapping for a group of fields.
Tier 0.

## What needed doing

Every real form needs the same plumbing — block submission while a field is
invalid, focus the first invalid control, hold server-side field errors
somewhere a `Field` can read from — and none of that should be
re-implemented per feature slice.

## What was actually done

Thin wrapper over `@base-ui/react/form`. Native submission handling,
blocking submit on an invalid field, focusing the first invalid control, and
holding `errors` (server-side field errors, keyed by field name) are all
Base UI's, verified against the installed package's own `Form.d.ts` rather
than assumed. This component adds only the default field-to-field layout
(`gap-stack`) and this package's own type discipline: `FormValues` is
generic like `ToggleGroup`'s `Value` parameter (so a typed caller doesn't
have to widen to a bag type), bounded by `Record<string, unknown>` rather
than Base UI's own `Record<string, any>` default, since `no-explicit-any` is
a workspace-wide rule and `unknown` satisfies Base UI's constraint without
reintroducing the type hole.

One real, load-bearing finding from building this alongside `Input`: this
package's own `Field` does **not** read `errors` from `Form`'s context
automatically — confirmed by reading `FieldError.js` directly, not assumed.
`Field.Error`'s displayed text is whatever string is passed into `Field`'s
own `error` prop; a real call site has to read `errors[name]` itself and
pass it in. This is deliberate, not a gap: a Tier 0 component holding "which
key in `errors` belongs to this field" would be holding caller-specific
knowledge it has no business carrying. `Form.test.tsx`'s integration test
exercises exactly this pattern.

## SOLID

Single responsibility: submission mechanics and layout, nothing about what
the fields inside mean. Dependency inversion: validation blocking, focus
management and error state all come from Base UI; a future change to any of
those needs no change here, only to whatever calls `useFormContext` or reads
`errors` downstream.
