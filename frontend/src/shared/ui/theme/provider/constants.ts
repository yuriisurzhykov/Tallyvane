/**
 * Its own file with no `"use client"` directive, and that is not tidiness.
 *
 * React Server Components treat a `"use client"` module as an opaque client
 * reference: only its COMPONENT exports cross the boundary correctly. A plain
 * value exported from one arrives as `undefined` on the server side — so a
 * server component reading this key from the provider module would call
 * `localStorage.getItem(undefined)`, quietly read a key literally named
 * "undefined", and break the pre-hydration script with no error anywhere.
 *
 * A directive-free file is safe to import from either side of that boundary.
 */
export const THEME_STORAGE_KEY = "tallyvane.theme-preference";

/**
 * The class list the provider and the inline script both manage. Dark carries
 * no rules of its own — it compiles to `:root` — but the class is applied
 * anyway so the DOM says which theme is active rather than implying it by the
 * absence of the other.
 */
export const THEME_CLASS = {
    dark: "theme-dark",
    light: "theme-light",
    system: "theme-system",
} as const;
