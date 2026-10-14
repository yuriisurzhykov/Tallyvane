"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useApi } from "frontend-shared/api";
import { SignIns } from "../api/SignIns";
import type { SignInState } from "./SignInState";

export type SignInStatus =
    | { readonly status: "loading" }
    | { readonly status: "ready"; readonly state: SignInState }
    /** The browser has no sign-in in progress. */
    | { readonly status: "none" }
    | { readonly status: "failed" };

export interface SignInView {
    readonly status: SignInStatus;
    /** Reads where the attempt stands again, showing `loading` meanwhile: what is on the screen may no longer be true. */
    readonly refresh: () => void;
}

/** Where the sign-in stands, read when the page opens and whenever asked. Never remembered: the server decides. */
export function useSignInState(): SignInView {
    const api = useApi();
    const signIns = useMemo(() => new SignIns(api), [api]);
    const [status, setStatus] = useState<SignInStatus>({ status: "loading" });
    const [reads, setReads] = useState(0);

    useEffect(() => {
        let current = true;
        signIns.state().then(
            (state) => {
                if (current) setStatus(state === undefined ? { status: "none" } : { status: "ready", state });
            },
            () => {
                if (current) setStatus({ status: "failed" });
            },
        );
        return () => {
            current = false;
        };
    }, [signIns, reads]);

    const refresh = useCallback(() => {
        setStatus({ status: "loading" });
        setReads((count) => count + 1);
    }, []);

    return { status, refresh };
}
