export type ClassValue = string | number | boolean | undefined | null | readonly ClassValue[] | Readonly<Record<string, boolean | undefined | null>>;

/**
 * A minimal, dependency-free `clsx`-equivalent. Every component so far has
 * merged classes with a plain ternary (`className ? \`${base} ${className}\` : base`)
 * — this exists only to stop that pattern from being retyped by hand at every
 * call site, not to add a class-merging *library*. No new dependency: string,
 * falsy-filtering and array/object handling cover what every component in
 * this package actually needs, without `clsx`'s package boundary.
 */
export function cn(...inputs: readonly ClassValue[]): string {
    const classes: string[] = [];

    for (const input of inputs) {
        if (!input) continue;

        if (typeof input === "string" || typeof input === "number") {
            classes.push(String(input));
        } else if (Array.isArray(input)) {
            const nested = cn(...(input as readonly ClassValue[]));
            if (nested) classes.push(nested);
        } else {
            for (const [key, value] of Object.entries(input)) {
                if (value) classes.push(key);
            }
        }
    }

    return classes.join(" ");
}
