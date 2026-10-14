/**
 * A prop that is left off when there is nothing to put in it, spread into a component's props. With
 * `exactOptionalPropertyTypes` an optional `error?: string` cannot be given `undefined`, so the choice
 * between "pass it" and "leave it off" is made here once instead of at every call site.
 */
export function optionally<Name extends string>(name: Name, value: string | undefined): Partial<Record<Name, string>> {
    return value === undefined ? {} : ({ [name]: value } as Record<Name, string>);
}
