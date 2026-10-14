"use client";

import { useCallback, useEffect, useState } from "react";

export type Loaded<T> =
    | { readonly status: "loading" }
    /** `stale` when the last read failed: the value is what the screen had, and may be out of date. */
    | { readonly status: "ready"; readonly value: T; readonly stale: boolean }
    | { readonly status: "failed" };

export interface LoadedView<T> {
    readonly state: Loaded<T>;
    /** Reads again. A value already on the screen stays there until the new one arrives. */
    readonly refresh: () => void;
}

interface Outcome<T> {
    readonly load: () => Promise<T>;
    readonly state: Loaded<T>;
}

/**
 * Something the server holds, read when the screen opens and whenever asked. `load` must keep its identity
 * between renders (`useMemo`/`useCallback`); a new `load` means a different question (another purpose, another
 * client), so what was answered to the old one is no longer shown.
 */
export function useLoaded<T>(load: () => Promise<T>): LoadedView<T> {
    const [outcome, setOutcome] = useState<Outcome<T> | undefined>(undefined);
    const [reads, setReads] = useState(0);

    useEffect(() => {
        let current = true;
        load().then(
            (value) => {
                if (current) setOutcome({ load, state: { status: "ready", value, stale: false } });
            },
            () => {
                if (current) {
                    setOutcome((now) =>
                        now?.load === load && now.state.status === "ready"
                            ? { load, state: { ...now.state, stale: true } }
                            : { load, state: { status: "failed" } },
                    );
                }
            },
        );
        return () => {
            current = false;
        };
    }, [load, reads]);

    const refresh = useCallback(() => {
        setOutcome((now) => (now?.state.status === "failed" ? undefined : now));
        setReads((count) => count + 1);
    }, []);

    const state: Loaded<T> = outcome?.load === load ? outcome.state : { status: "loading" };
    return { state, refresh };
}

/** The same state with its value shown another way, e.g. a history as the list of its versions. Loading and failure pass through. */
export function mapLoaded<T, R>(state: Loaded<T>, change: (value: T) => R): Loaded<R> {
    return state.status === "ready" ? { status: "ready", value: change(state.value), stale: state.stale } : state;
}
