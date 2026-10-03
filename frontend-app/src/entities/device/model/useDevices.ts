"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useApi } from "frontend-shared/api";
import { Devices } from "../api/Devices";
import type { Device } from "./Device";

export type DevicesState =
    | { readonly status: "loading" }
    | { readonly status: "ready"; readonly devices: readonly Device[] }
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
                if (current) setState({ status: "ready", devices: list });
            },
            () => {
                if (current) setState({ status: "failed" });
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
