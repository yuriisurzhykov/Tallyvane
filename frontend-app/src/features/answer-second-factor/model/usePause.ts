"use client";

import { useCallback, useEffect, useState } from "react";

export interface Pause {
    /** Whole seconds still to wait; `0` when nothing is being waited for. */
    readonly secondsLeft: number;
    /** Begins waiting for that many seconds, replacing a wait already running. */
    readonly start: (seconds: number) => void;
}

const TICK_MS = 250;

/**
 * A pause the server asked for, counted down against the clock rather than by ticks, so a tab that was
 * asleep for a while does not come back with a wait that never shortened. The clock is only read, and
 * the state only set, from a timer callback or an event, never from the effect body itself.
 */
export function usePause(initialSeconds: number): Pause {
    const [until, setUntil] = useState(() => Date.now() + initialSeconds * 1000);
    const [now, setNow] = useState(() => Date.now());

    useEffect(() => {
        const timer = window.setInterval(() => {
            const at = Date.now();
            setNow(at);
            if (at >= until) {
                window.clearInterval(timer);
            }
        }, TICK_MS);
        return () => {
            window.clearInterval(timer);
        };
    }, [until]);

    const start = useCallback((seconds: number) => {
        const at = Date.now();
        setNow(at);
        setUntil(at + seconds * 1000);
    }, []);

    return { secondsLeft: Math.max(0, Math.ceil((until - now) / 1000)), start };
}
