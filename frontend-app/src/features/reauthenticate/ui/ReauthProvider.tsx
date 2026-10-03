"use client";

import { useEffect, useState, type ReactNode } from "react";
import { ApiProvider, createApi, useApi } from "frontend-shared/api";
import { GoogleSignIn, GoogleStepUp, useViewer } from "@/entities/viewer";
import { ConfirmationSignal } from "../model/ConfirmationSignal";
import { PopupHandoff } from "../model/PopupHandoff";
import { ReauthSession } from "../model/ReauthSession";
import { SignInSignal } from "../model/SignInSignal";
import { StepUpSession } from "../model/StepUpSession";
import { ReauthDialog } from "./ReauthDialog";
import { StepUpDialog } from "./StepUpDialog";

export interface ReauthProviderProps {
    readonly children: ReactNode;
}

/**
 * Gives everything below it an API that, when the session ends under a request, asks the person to sign
 * in again and then repeats the request. The same call sites, no handling of their own. If the person
 * who comes back is a different one, the page is reloaded instead: the work in it was not theirs.
 *
 * It also asks for a recent proof when a dangerous act is refused for want of one (ADR-092), and repeats
 * the act once the person has given it.
 */
export function ReauthProvider({ children }: ReauthProviderProps) {
    const plain = useApi();
    const viewer = useViewer();
    const [session] = useState(() => new ReauthSession());
    const [stepUpSession] = useState(() => new StepUpSession());
    const [guarded] = useState(() => createApi({ origin: "", onExpired: session, onStepUp: stepUpSession }));
    const [signIn] = useState(() => new GoogleSignIn(plain));
    const [stepUp] = useState(() => new GoogleStepUp(plain));
    const [handoff] = useState(() => new PopupHandoff());

    useEffect(
        () =>
            new SignInSignal().listen(() => {
                plain.get("/me").then(
                    (me) => {
                        if (viewer.is(me.id)) {
                            session.confirm();
                        } else {
                            window.location.reload();
                        }
                    },
                    () => undefined,
                );
            }),
        [plain, viewer, session],
    );

    useEffect(
        () =>
            new ConfirmationSignal().listen(() => {
                stepUpSession.done();
            }),
        [stepUpSession],
    );

    return (
        <ApiProvider api={guarded}>
            {children}
            <ReauthDialog session={session} signIn={signIn} handoff={handoff} />
            <StepUpDialog session={stepUpSession} stepUp={stepUp} />
        </ApiProvider>
    );
}
