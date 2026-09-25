import { useEffect, useState } from "react";
import type { AuthPageKind } from "../../../features/authentication/model/AuthPageKind";
import { restoreCurrentSession } from "../../../features/authentication/model/restore-current-session";

type Navigate = (path: string, replace?: boolean) => void;

export function useAuthSessionRedirect(
    kind: AuthPageKind,
    destination: string,
    navigate: Navigate,
): boolean {
    const checksSession = kind !== "security" && kind !== "preview";
    const [ready, setReady] = useState(!checksSession);

    useEffect(() => {
        if (!checksSession) {
            setReady(true);
            return;
        }

        let active = true;
        setReady(false);
        void restoreCurrentSession().then(hasSession => {
            if (!active) return;
            if (hasSession) navigate(destination, true);
            else setReady(true);
        });

        return () => { active = false; };
    }, [checksSession, destination, navigate]);

    return ready;
}
