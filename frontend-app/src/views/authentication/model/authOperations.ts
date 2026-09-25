import type { Dispatch, SetStateAction, SyntheticEvent } from "react";
import type { AuthPageKind } from "../../../features/authentication/model/AuthPageKind";
import { authClient, AuthError } from "../../../features/authentication/api/client";
import type { AuthStringKey } from "../../../features/authentication/model/strings";
import type { AuthSession } from "../../../widgets/authentication-step/model/AuthStepProps";

type Translate = (key: AuthStringKey, vars?: Record<string, string | number>) => string;
type Tone = "attention" | "danger" | "success";
type Notify = (title: string, description: string | undefined, tone: Tone) => void;
type SubmitEvent = SyntheticEvent<HTMLFormElement>;
export interface Registration {
    challengeId?: string | null;
    email: string;
}

export interface AuthOperationsState {
    readonly kind: AuthPageKind;
    readonly t: Translate;
    readonly notify: Notify;
    readonly navigate: (path: string) => void;
    readonly code: string;
    readonly setCode: Dispatch<SetStateAction<string>>;
    readonly factor: string;
    readonly payload: string;
    readonly email: string;
    readonly setEmail: Dispatch<SetStateAction<string>>;
    readonly emailMfaChallengeId: string;
    readonly setEmailMfaChallengeId: Dispatch<SetStateAction<string>>;
    readonly emailSignInChallengeId: string;
    readonly setEmailSignInChallengeId: Dispatch<SetStateAction<string>>;
    readonly otpPurpose: string;
    readonly setOtpPurpose: Dispatch<SetStateAction<string>>;
    readonly registration: Registration | null;
    readonly setRegistration: Dispatch<SetStateAction<Registration | null>>;
    readonly registrationResendSeconds: number;
    readonly setRegistrationResendSeconds: Dispatch<SetStateAction<number>>;
    readonly passwordResetChallengeId: string;
    readonly setPasswordResetChallengeId: Dispatch<SetStateAction<string>>;
    readonly emailMfaEnrollmentChallengeId: string;
    readonly setEmailMfaEnrollmentChallengeId: Dispatch<SetStateAction<string>>;
    readonly setNotice: Dispatch<SetStateAction<string>>;
    readonly setRequiredEnrollmentComplete: Dispatch<SetStateAction<boolean>>;
    readonly setPayload: Dispatch<SetStateAction<string>>;
    readonly setRecoveryCodes: Dispatch<SetStateAction<string[]>>;
    readonly setSessions: Dispatch<SetStateAction<AuthSession[]>>;
    readonly setBusy: Dispatch<SetStateAction<boolean>>;
    readonly busy: boolean;
    readonly clearErrors: () => void;
    readonly applyFieldErrors: (errors: Readonly<Record<string, string>>) => void;
};

export function formText(form: FormData, name: string): string {
    const value = form.get(name);
    return typeof value === "string" ? value : "";
}

function announceUnexpectedStep(state: AuthOperationsState) {
    state.notify(state.t("signInContinuedError"), state.t("unknownAuthStep"), "danger");
}

function continueSignIn(
    result: { status: string; pendingId?: string; availableMethods?: string[] },
    state: AuthOperationsState,
) {
    if (result.status === "issued") {
        state.navigate("/today");
        return;
    }
    if (!result.pendingId) {
        announceUnexpectedStep(state);
        return;
    }
    if (result.status === "requires_second_factor") {
        sessionStorage.setItem("tallyvane.pendingId", result.pendingId);
        sessionStorage.setItem("tallyvane.availableMethods", JSON.stringify(result.availableMethods ?? []));
        state.navigate("/mfa");
        return;
    }
    if (result.status === "requires_enrollment") {
        sessionStorage.setItem("tallyvane.pendingEnrollmentId", result.pendingId);
        sessionStorage.setItem("tallyvane.requiredMethods", JSON.stringify(result.availableMethods ?? []));
        state.navigate(`/mfa/enroll?pending_id=${encodeURIComponent(result.pendingId)}`);
        return;
    }
    announceUnexpectedStep(state);
}

async function login(form: FormData, state: AuthOperationsState) {
    const result = await authClient.post<{
        status: string;
        pendingId?: string;
        availableMethods?: string[];
    }>("/login/password", {
        email: formText(form, "email"),
        password: formText(form, "password"),
        device: "Browser",
    });
    continueSignIn(result, state);
}

async function register(form: FormData, state: AuthOperationsState) {
    const email = formText(form, "email");
    const result = await authClient.post<{ userId: string; challengeId: string | null }>("/register/password", {
        email,
        password: formText(form, "password"),
        displayName: formText(form, "name") || null,
    });
    const registration = { challengeId: result.challengeId, email };
    sessionStorage.setItem("tallyvane.registration", JSON.stringify(registration));
    state.setRegistration(registration);
    state.setEmail(email);
    state.setOtpPurpose("registration");
    state.setRegistrationResendSeconds(60);
    if (!result.challengeId) {
        state.notify(state.t("accountCreated"), state.t("delayedMail"), "attention");
    }
    state.navigate("/otp?purpose=registration");
}

async function verifyMfa(state: AuthOperationsState) {
    const pendingId = sessionStorage.getItem("tallyvane.pendingId");
    if (!pendingId) throw new Error(state.t("signInExpired"));
    if (state.factor === "EMAIL_OTP" && !state.emailMfaChallengeId) {
        const result = await authClient.post<{ challengeId: string }>("/mfa/email/request", { pendingId });
        state.setEmailMfaChallengeId(result.challengeId);
        state.notify(state.t("mfaEmailCodeSent"), state.t("mfaEmailCodeDescription"), "success");
        return;
    }
    const result = await authClient.post<{ status: string }>("/mfa/verify", {
        pendingId,
        kind: state.factor,
        code: state.code,
        ...(state.factor === "EMAIL_OTP" ? { challengeId: state.emailMfaChallengeId } : {}),
    });
    if (result.status === "issued") {
        sessionStorage.removeItem("tallyvane.pendingId");
        state.navigate("/today");
    }
}

async function enrollAuthenticator(state: AuthOperationsState) {
    const pendingId = new URLSearchParams(window.location.search).get("pending_id") ??
        sessionStorage.getItem("tallyvane.pendingEnrollmentId");
    if (!state.payload) {
        const result = pendingId
            ? await authClient.post<{ otpauthUri: string }>("/mfa/required/enroll", { pendingId, kind: "TOTP" })
            : await authClient.post<{ otpauthUri: string }>("/mfa/enroll", { kind: "TOTP" });
        state.setPayload(result.otpauthUri);
        return;
    }
    if (pendingId) {
        await authClient.post("/mfa/required/confirm", { pendingId, kind: "TOTP", code: state.code });
    } else {
        await authClient.post("/mfa/confirm", { kind: "TOTP", code: state.code });
    }
    const required = Boolean(pendingId);
    state.notify(
        state.t("authenticatorEnabled"),
        state.t(required ? "requiredAuthenticatorEnabledDescription" : "authenticatorEnabledDescription"),
        "success",
    );
    if (pendingId) {
        sessionStorage.removeItem("tallyvane.pendingEnrollmentId");
        sessionStorage.removeItem("tallyvane.requiredMethods");
        state.setNotice(state.t("requiredAuthenticatorConfirmed"));
        state.setRequiredEnrollmentComplete(true);
        window.history.replaceState(null, "", window.location.pathname);
    } else {
        state.setNotice(state.t("authenticatorConfirmed"));
    }
    state.setPayload("");
    state.setCode("");
}

async function verifyOtp(state: AuthOperationsState) {
    if (state.otpPurpose === "login") {
        await verifyEmailSignIn(state);
        return;
    }
    await verifyRegistration(state);
}

async function verifyEmailSignIn(state: AuthOperationsState) {
    if (!state.emailSignInChallengeId) {
        const result = await authClient.post<{ challengeId: string }>("/login/email/code", { email: state.email });
        state.setEmailSignInChallengeId(result.challengeId);
        state.setCode("");
        state.notify(state.t("signInCodeSent"), state.t("checkInboxForCode", { email: state.email }), "success");
        return;
    }
    const result = await authClient.post<{
        status: string;
        pendingId?: string;
        availableMethods?: string[];
    }>("/login/email/verify", {
        challengeId: state.emailSignInChallengeId,
        email: state.email,
        code: state.code,
        device: "Browser",
    });
    continueSignIn(result, state);
}

async function verifyRegistration(state: AuthOperationsState) {
    const pending = state.registration ?? JSON.parse(sessionStorage.getItem("tallyvane.registration") ?? "null") as Registration | null;
    if (!pending) throw new Error(state.t("missing"));
    if (!pending.challengeId) throw new Error(state.t("requestNewCodeFirst"));
    await authClient.post("/register/email/verify", {
        challengeId: pending.challengeId,
        email: pending.email,
        code: state.code,
    });
    state.notify(state.t("emailVerified"), state.t("emailVerifiedDescription"), "success");
    sessionStorage.removeItem("tallyvane.registration");
    state.setRegistration(null);
    state.setNotice(state.t("emailVerifiedNotice"));
}

async function recoverPassword(form: FormData, state: AuthOperationsState) {
    if (!state.passwordResetChallengeId) {
        const result = await authClient.post<{ challengeId: string }>("/password/forgot", { email: state.email });
        state.setPasswordResetChallengeId(result.challengeId);
        state.setCode("");
        state.notify(state.t("resetCodeSent"), state.t("checkInboxForCode", { email: state.email }), "success");
        return;
    }
    await authClient.post("/password/reset", {
        challengeId: state.passwordResetChallengeId,
        email: state.email,
        code: state.code,
        newPassword: formText(form, "newPassword"),
    });
    state.setPasswordResetChallengeId("");
    state.setNotice(state.t("passwordUpdatedNotice"));
    state.notify(state.t("passwordUpdated"), state.t("passwordUpdatedDescription"), "success");
}

async function changePassword(form: FormData, state: AuthOperationsState) {
    await authClient.post("/account/password", {
        currentPassword: formText(form, "currentPassword"),
        newPassword: formText(form, "newPassword"),
    });
    state.setNotice(state.t("passwordChanged"));
    state.notify(state.t("passwordChanged"), undefined, "success");
}

async function submitForKind(form: FormData, state: AuthOperationsState) {
    switch (state.kind) {
        case "login": await login(form, state); return;
        case "register": await register(form, state); return;
        case "mfa": await verifyMfa(state); return;
        case "enrollment": await enrollAuthenticator(state); return;
        case "otp": await verifyOtp(state); return;
        case "forgot": await recoverPassword(form, state); return;
        case "security": await changePassword(form, state); return;
        case "google":
        case "callback":
        case "preview": state.setNotice(state.t("previewActionNotice"));
    }
}

export async function submitAuthForm(
    event: SubmitEvent,
    state: AuthOperationsState,
) {
    event.preventDefault();
    state.setBusy(true);
    state.clearErrors();
    state.setNotice("");
    try {
        await submitForKind(new FormData(event.currentTarget), state);
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

function errorMessageKey(reason: unknown): AuthStringKey {
    if (!(reason instanceof AuthError)) return "requestFailed";
    if (reason.status === 0) return "networkError";
    if (reason.status === 401) return "sessionExpiredError";
    if (reason.status === 403) return "securitySessionError";
    if (reason.status === 429) return "rateLimitedError";
    return "requestFailed";
}

export function messageForAuthError(reason: unknown, t: Translate): string {
    return t(errorMessageKey(reason));
}
