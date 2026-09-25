import type { SyntheticEvent } from "react";
import { authClient, AuthError } from "../../../features/authentication/api/client";
import type { AuthOperationsState } from "./authOperations";
import type { Registration } from "./authOperations";
import { formText, messageForAuthError } from "./authOperations";

type AuthFormEvent = SyntheticEvent<HTMLFormElement>;
export async function resendRegistration(
    state: AuthOperationsState,
) {
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

export async function issueRecoveryCodes(event: AuthFormEvent, state: AuthOperationsState) {
    event.preventDefault();
    state.setBusy(true);
    state.clearErrors();
    state.setNotice("");
    const target = event.currentTarget;
    try {
        const result = await authClient.post<{ codes: string[] }>("/mfa/backup-codes", {
            currentPassword: formText(new FormData(target), "backupCurrentPassword"),
        });
        state.setRecoveryCodes(result.codes);
        state.setNotice(state.t("recoveryCodesIssued"));
        state.notify(state.t("recoveryCodesIssued"), state.t("recoveryCodesOneTime"), "success");
        target.reset();
    } catch (reason: unknown) {
        if (reason instanceof AuthError && Object.keys(reason.fieldErrors).length > 0) {
            state.applyFieldErrors(reason.fieldErrors);
        } else {
            state.notify(state.t("authFailed"), messageForAuthError(reason, state.t), "danger");
        }
    } finally {
        state.setBusy(false);
    }
}

export async function enrollEmailFactor(event: AuthFormEvent, state: AuthOperationsState) {
    event.preventDefault();
    state.setBusy(true);
    state.clearErrors();
    const form = new FormData(event.currentTarget);
    try {
        if (!state.emailMfaEnrollmentChallengeId) {
            const result = await authClient.post<{ challengeId: string }>("/mfa/email/enroll", {
                currentPassword: formText(form, "emailMfaPassword"),
            });
            state.setEmailMfaEnrollmentChallengeId(result.challengeId);
            state.notify(state.t("emailMfaCodeSent"), state.t("emailMfaCodeSentHelp"), "success");
        } else {
            await authClient.post("/mfa/email/confirm", {
                challengeId: state.emailMfaEnrollmentChallengeId,
                code: formText(form, "emailMfaCode"),
            });
            state.setEmailMfaEnrollmentChallengeId("");
            state.setNotice(state.t("emailMfaEnabledNotice"));
            state.notify(state.t("emailMfaEnabled"), undefined, "success");
        }
    } catch (reason: unknown) {
        if (reason instanceof AuthError && Object.keys(reason.fieldErrors).length > 0) {
            state.applyFieldErrors(reason.fieldErrors);
        } else {
            state.notify(state.t("authFailed"), messageForAuthError(reason, state.t), "danger");
        }
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
