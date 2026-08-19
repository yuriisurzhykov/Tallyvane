export type ThemeId = "dark" | "light";

/** What the reader chose. `system` follows the operating system, but is always resolved down to one of the two concrete themes before anything renders. */
export type ThemePreference = ThemeId | "system";

export interface ThemeContextValue {
    /**
     * The theme actually in effect, after `system` has been resolved.
     * */
    readonly theme: ThemeId;
    /**
     * The raw choice, which may still be `system`.
     * */
    readonly preference: ThemePreference;
    readonly setPreference: (value: ThemePreference) => void;
}
