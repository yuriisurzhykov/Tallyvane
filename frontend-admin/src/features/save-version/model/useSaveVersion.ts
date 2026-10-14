"use client";

import { useCallback, useState } from "react";
import { Rejection } from "@/shared/rejection";

export type SavingState =
    | { readonly status: "idle" }
    | { readonly status: "saving" }
    /** The server named what is wrong; nothing was saved. */
    | { readonly status: "rejected"; readonly rejection: Rejection }
    | { readonly status: "failed" }
    /** Kept as this version. It is not in force: that is a separate act. */
    | { readonly status: "saved"; readonly number: number };

export interface SaveVersion {
    readonly state: SavingState;
    /** Runs the attempt, which keeps the version and says its number, and follows it through. */
    readonly save: (attempt: () => Promise<number>) => void;
}

/**
 * Keeping a new version. A refusal that names fields is a state the form shows beside itself; anything else
 * is a failure to try again. Either way the form stays as it was, so nothing the person typed is lost.
 */
export function useSaveVersion(onSaved: (number: number) => void): SaveVersion {
    const [state, setState] = useState<SavingState>({ status: "idle" });

    const save = useCallback(
        (attempt: () => Promise<number>) => {
            setState({ status: "saving" });
            attempt().then(
                (number) => {
                    setState({ status: "saved", number });
                    onSaved(number);
                },
                (failure: unknown) => {
                    const rejection = Rejection.from(failure);
                    setState(rejection === undefined ? { status: "failed" } : { status: "rejected", rejection });
                },
            );
        },
        [onSaved],
    );

    return { state, save };
}
