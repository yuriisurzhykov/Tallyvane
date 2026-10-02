"use client";

import { useEffect, useState, type ReactNode } from "react";
import { ApiProvider, createApi, useApi } from "frontend-shared/api";
import { GoogleSignIn, useViewer } from "@/entities/viewer";
import { PopupHandoff } from "../model/PopupHandoff";
import { ReauthSession } from "../model/ReauthSession";
import { SignInSignal } from "../model/SignInSignal";
import { ReauthDialog } from "./ReauthDialog";

export interface ReauthProviderProps {
    readonly children: ReactNode;
}

/**
 * Gives everything below it an API that, when the session ends under a request, asks the person to sign
 * in again and then repeats the request. The same call sites, no handling of their own. If the person
 * who comes back is a different one, the page is reloaded instead: the work in it was not theirs.
 */
export function ReauthProvider({ children }: ReauthProviderProps) {
    const plain = useApi();
    const viewer = useViewer();
    const [session] = useState(() => new ReauthSession());
    const [guarded] = useState(() => createApi({ origin: "", onExpired: session }));
    const [signIn] = useState(() => new GoogleSignIn(plain));
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

    return (
        <ApiProvider api={guarded}>
            {children}
            <ReauthDialog session={session} signIn={signIn} handoff={handoff} />
        </ApiProvider>
    );
}
