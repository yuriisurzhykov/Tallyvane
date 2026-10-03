"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useApi } from "frontend-shared/api";
import { Devices } from "../api/Devices";
import type { Device } from "./Device";

export type DevicesState =
    | { readonly status: "loading" }
    /** `stale` when the last read failed: the list is what the screen had, and may be out of date. */
    | { readonly status: "ready"; readonly devices: readonly Device[]; readonly stale: boolean }
    | { readonly status: "failed" };

export interface DevicesView {
    readonly state: DevicesState;
    /** Reads the list again. A list already on the screen stays there until the new one arrives. */
    readonly refresh: () => void;
}

/** The person's devices as the server has them now, read when the screen opens and whenever asked. */
export function useDevices(): DevicesView {
    const api = useApi();
    const devices = useMemo(() => new Devices(api), [api]);
    const [state, setState] = useState<DevicesState>({ status: "loading" });
    const [reads, setReads] = useState(0);

    useEffect(() => {
        let current = true;
        devices.list().then(
            (list) => {
                if (current) setState({ status: "ready", devices: list, stale: false });
            },
            () => {
                if (current) setState((now) => (now.status === "ready" ? { ...now, stale: true } : { status: "failed" }));
            },
        );
        return () => {
            current = false;
        };
    }, [devices, reads]);

    const refresh = useCallback(() => {
        setState((now) => (now.status === "failed" ? { status: "loading" } : now));
        setReads((count) => count + 1);
    }, []);

    return { state, refresh };
}
