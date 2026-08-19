"use client";

import * as React from "react";
import { THEME_CLASS, THEME_STORAGE_KEY } from "./constants";
import type { ThemeContextValue, ThemeId, ThemePreference } from "./theme.types";

const ThemeContext = React.createContext<ThemeContextValue | null>(null);

/**
 * What the server renders, and therefore what the client's hydration render
 * must produce too. Dark, per the specification's §12.
 */
const SERVER_THEME: ThemeId = "dark";

/**
 * Both pieces of state this provider needs live outside React — one in
 * `localStorage`, one in the operating system — and both are read through
 * `useSyncExternalStore` rather than copied into state by an effect.
 *
 * The distinction is not stylistic. Reading an external value during render
 * gives the server and the client's first render different answers, which is
 * precisely what a hydration mismatch is; copying it in an effect afterwards
 * fixes the mismatch but re-renders the whole tree and leaves the wrong value
 * on screen until it does. `useSyncExternalStore` exists for exactly this
 * shape: it renders the server's answer during hydration and switches to the
 * live one immediately after, with React aware that the two differ on purpose.
 */

const preferenceListeners = new Set<() => void>();

function notifyPreferenceChanged(): void {
    for (const listener of preferenceListeners) listener();
}

function subscribeToPreference(onChange: () => void): () => void {
    preferenceListeners.add(onChange);
    // `storage` fires only in OTHER tabs, which is the point: a preference
    // changed in one window should follow the reader into the next.
    window.addEventListener("storage", onChange);
    return () => {
        preferenceListeners.delete(onChange);
        window.removeEventListener("storage", onChange);
    };
}

function readPreference(): ThemePreference {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    if (stored === "dark" || stored === "light" || stored === "system") return stored;
    // Never chosen: follow the operating system rather than pinning whatever
    // the server happened to render.
    return "system";
}

function subscribeToSystem(onChange: () => void): () => void {
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    media.addEventListener("change", onChange);
    return () => { media.removeEventListener("change", onChange); };
}

function readSystemPrefersDark(): boolean {
    return window.matchMedia("(prefers-color-scheme: dark)").matches;
}

function applyThemeClass(theme: ThemeId): void {
    const root = document.documentElement;
    root.classList.remove(THEME_CLASS.dark, THEME_CLASS.light);
    root.classList.add(THEME_CLASS[theme]);
}

export function ThemeProvider({ children }: { readonly children: React.ReactNode }) {
    const preference = React.useSyncExternalStore(
        subscribeToPreference,
        readPreference,
        () => SERVER_THEME as ThemePreference,
    );
    const systemPrefersDark = React.useSyncExternalStore(
        subscribeToSystem,
        readSystemPrefersDark,
        () => SERVER_THEME === "dark",
    );

    // Derived during render, not stored. There is no state here to fall out of
    // step with its inputs, and no effect that has to remember to run.
    const theme: ThemeId = preference === "system" ? (systemPrefersDark ? "dark" : "light") : preference;

    /**
     * `useLayoutEffect`, and this is the one place an effect is right: writing
     * a class onto an element React does not own is a side effect, not derived
     * state.
     *
     * Layout rather than passive, because of a case the inline script cannot
     * cover: React's Strict Mode remounts the tree in development and resets
     * the root element's classes to only those it manages, wiping what the
     * script set. A layout effect restores it before the browser paints; a
     * passive one lets the wrong theme flash back in.
     */
    React.useLayoutEffect(() => { applyThemeClass(theme); }, [theme]);

    /** Persistence belongs to the act of choosing. Writing on every render would turn "never chose" into "chose whatever was showing". */
    const setPreference = React.useCallback((value: ThemePreference) => {
        window.localStorage.setItem(THEME_STORAGE_KEY, value);
        notifyPreferenceChanged();
    }, []);

    const value = React.useMemo<ThemeContextValue>(
        () => ({ theme, preference, setPreference }),
        [theme, preference, setPreference],
    );

    return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export { ThemeContext };
