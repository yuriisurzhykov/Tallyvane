import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";

/**
 * `@testing-library/react`'s automatic `cleanup()` only registers itself when
 * it detects a global `afterEach` (Vitest's `test.globals: true`). Globals are
 * deliberately off here — `describe`/`it`/`expect` are imported explicitly in
 * every spec, so no ESLint environment change is needed — which means cleanup
 * has to be wired by hand, or a component left mounted by one test starts
 * appearing in the next one's queries.
 */
afterEach(() => {
    cleanup();
});

/**
 * jsdom does not implement `matchMedia` at all. A no-op stub that always
 * returns `matches: false` is worse than nothing: it silently answers every
 * media query the same way, so a component branching on "system prefers dark"
 * or "reduced motion" cannot be tested in either state. This stub tracks real
 * listeners so a test can flip `matches` and dispatch a `change` event to
 * exercise both branches.
 */
function createMatchMediaStub(): (query: string) => MediaQueryList {
    return (query: string) => {
        const listeners = new Set<(event: MediaQueryListEvent) => void>();
        const mediaQueryList: MediaQueryList = {
            matches: false,
            media: query,
            onchange: null,
            addEventListener: (type: string, listener: EventListenerOrEventListenerObject) => {
                if (type === "change") listeners.add(listener as (event: MediaQueryListEvent) => void);
            },
            removeEventListener: (type: string, listener: EventListenerOrEventListenerObject) => {
                if (type === "change") listeners.delete(listener as (event: MediaQueryListEvent) => void);
            },
            // Deprecated, but some libraries still call these instead of the
            // standard EventTarget methods above.
            addListener: (listener) => {
                if (listener) listeners.add(listener as (event: MediaQueryListEvent) => void);
            },
            removeListener: (listener) => {
                if (listener) listeners.delete(listener as (event: MediaQueryListEvent) => void);
            },
            dispatchEvent: (event) => {
                listeners.forEach((listener) => { listener(event as MediaQueryListEvent); });
                return true;
            },
        };
        return mediaQueryList;
    };
}

Object.defineProperty(window, "matchMedia", {
    writable: true,
    configurable: true,
    value: createMatchMediaStub(),
});
