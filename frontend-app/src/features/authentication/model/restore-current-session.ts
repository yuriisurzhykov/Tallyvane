import { AuthError, authClient } from "../api/client";

type SessionState = "active" | "expired" | "unavailable";

async function readSessionState(): Promise<SessionState> {
    try {
        await authClient.get<void>("/session");
        return "active";
    } catch (reason) {
        return reason instanceof AuthError && reason.status === 401 ? "expired" : "unavailable";
    }
}

/** Confirms an existing session, refreshing an expired access token once across open tabs. */
export async function restoreCurrentSession(): Promise<boolean> {
    const initial = await readSessionState();
    if (initial === "active") return true;
    if (initial === "unavailable") return false;

    const refreshIfStillExpired = async () => {
        const current = await readSessionState();
        if (current === "active") return;
        if (current === "unavailable") throw new AuthError(0);
        await authClient.refreshSession();
    };

    try {
        const lockManager = typeof navigator === "undefined" ? undefined : navigator.locks;
        if (lockManager) {
            await lockManager.request("tallyvane.auth.refresh", refreshIfStillExpired);
        } else {
            await refreshIfStillExpired();
        }
        return (await readSessionState()) === "active";
    } catch {
        return false;
    }
}
