"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { refusalOf, type Refusal } from "./Refusal";
import type { Activation, Harmless } from "./Activation";

export type ActivationState<Consequences> =
    | { readonly status: "previewing" }
    | { readonly status: "previewFailed" }
    /** The consequences are on the screen and the person has not yet said yes. `refusal` is why the last yes did not go through. */
    | { readonly status: "ready"; readonly consequences: Consequences; readonly refusal?: Refusal }
    | { readonly status: "confirming"; readonly consequences: Consequences };

export interface ActivationView<Consequences> {
    readonly state: ActivationState<Consequences>;
    readonly confirm: (comment: string) => void;
}

/**
 * Looking at what a version would do, then putting it in force. The look comes first and always: a version
 * cannot be put in force without its consequences in front of the person. `onActivated` and `onGone` leave
 * the screen to read the list again.
 */
export function useActivation<Consequences extends Harmless>(
    activation: Activation<Consequences>,
    onActivated: () => void,
    onGone: () => void,
): ActivationView<Consequences> {
    // The panel is opened for one version and thrown away; whatever the parent builds on its next render is the same intention.
    const [subject] = useState(activation);
    const [state, setState] = useState<ActivationState<Consequences>>({ status: "previewing" });
    const leave = useRef({ onActivated, onGone });
    useEffect(() => {
        leave.current = { onActivated, onGone };
    }, [onActivated, onGone]);

    useEffect(() => {
        let current = true;
        subject.preview().then(
            (consequences) => {
                if (current) setState({ status: "ready", consequences });
            },
            (failure: unknown) => {
                if (!current) return;
                if (refusalOf(failure) === "gone") {
                    leave.current.onGone();
                } else {
                    setState({ status: "previewFailed" });
                }
            },
        );
        return () => {
            current = false;
        };
    }, [subject]);

    const confirm = useCallback(
        (comment: string) => {
            setState((now) => (now.status === "ready" ? { status: "confirming", consequences: now.consequences } : now));
            subject.confirm(comment).then(() => { leave.current.onActivated(); }, (failure: unknown) => {
                const refusal = refusalOf(failure);
                if (refusal === "gone") {
                    leave.current.onGone();
                    return;
                }
                setState((now) =>
                    now.status === "confirming"
                        ? { status: "ready", consequences: now.consequences, ...(refusal === "declined" ? {} : { refusal }) }
                        : now,
                );
            });
        },
        [subject],
    );

    return { state, confirm };
}
