import { authClient } from "../../../features/authentication/api/client";
import type { AuthOperationsState, Registration } from "./authOperations";
import { messageForAuthError } from "./authOperations";

export async function resendRegistration(state: AuthOperationsState) {
    const pending = state.registration ?? JSON.parse(sessionStorage.getItem("tallyvane.registration") ?? "null") as Registration | null;
    if (!pending || state.registrationResendSeconds > 0 || state.busy) return;
    state.setBusy(true);
    try {
        const result = await authClient.post<{ challengeId: string | null }>("/register/email/resend", {
            email: pending.email,
        });
        if (result.challengeId) {
            const updated = { ...pending, challengeId: result.challengeId };
            state.setRegistration(updated);
            sessionStorage.setItem("tallyvane.registration", JSON.stringify(updated));
            state.setCode("");
        }
        state.setRegistrationResendSeconds(60);
        state.notify(state.t("resendNeutralTitle"), state.t("resendNeutralDescription"), "success");
    } catch (reason: unknown) {
        state.notify(state.t("resendFailed"), messageForAuthError(reason, state.t), "danger");
    } finally {
        state.setBusy(false);
    }
}

export async function revokeAuthSession(sessionId: string, state: AuthOperationsState) {
    try {
        await authClient.remove<undefined>(`/sessions/${encodeURIComponent(sessionId)}`);
        state.setSessions(current => current.filter(session => session.id !== sessionId));
        state.notify(state.t("sessionRevoked"), undefined, "success");
    } catch (reason: unknown) {
        state.notify(state.t("sessionRevokeFailed"), messageForAuthError(reason, state.t), "danger");
    }
}
