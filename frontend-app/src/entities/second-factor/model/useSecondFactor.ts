"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useApi } from "frontend-shared/api";
import { SecondFactors } from "../api/SecondFactors";
import type { Standing } from "./Standing";

export type SecondFactorState =
    | { readonly status: "loading" }
    /** `stale` when the last read failed: the standing is what the screen had, and may be out of date. */
    | { readonly status: "ready"; readonly standing: Standing; readonly stale: boolean }
    | { readonly status: "failed" };

export interface SecondFactorView {
    readonly state: SecondFactorState;
    /** Reads the standing again. One already on the screen stays there until the new one arrives. */
    readonly refresh: () => void;
}

/** What the person has set up as a second factor, as the server has it now, read when the screen opens and whenever asked. */
export function useSecondFactor(): SecondFactorView {
    const api = useApi();
    const secondFactors = useMemo(() => new SecondFactors(api), [api]);
    const [state, setState] = useState<SecondFactorState>({ status: "loading" });
    const [reads, setReads] = useState(0);

    useEffect(() => {
        let current = true;
        secondFactors.standing().then(
            (standing) => {
                if (current) setState({ status: "ready", standing, stale: false });
            },
            () => {
                if (current) setState((now) => (now.status === "ready" ? { ...now, stale: true } : { status: "failed" }));
            },
        );
        return () => {
            current = false;
        };
    }, [secondFactors, reads]);

    const refresh = useCallback(() => {
        setState((now) => (now.status === "failed" ? { status: "loading" } : now));
        setReads((count) => count + 1);
    }, []);

    return { state, refresh };
}
