import { useEffect } from "react";
import QRCode from "qrcode";
import { authClient } from "../../../features/authentication/api/client";
import type { PrimarySignInMethod } from "../../../features/authentication/api/client";
import type { AuthPageKind } from "../../../features/authentication/model/AuthPageKind";
import type { AuthStringKey } from "../../../features/authentication/model/strings";
import type { AddToastOptions } from "frontend-shared/ui/toast";
import type { AuthPageState } from "./useAuthPageState";

type Translate = (key: AuthStringKey, vars?: Record<string, string | number>) => string;
type ErrorMessage = (reason: unknown, t: Translate) => string;
interface ToastActions {
    add(options: AddToastOptions): void;
}

export function useAuthPageEffects(options: {
    readonly kind: AuthPageKind;
    readonly state: AuthPageState;
    readonly t: Translate;
    readonly toast: ToastActions;
    readonly getErrorMessage: ErrorMessage;
}) {
    const { kind, state, t, toast, getErrorMessage } = options;
    useSignInOptions(kind, state, t, toast);
    useSecuritySessions({ kind, state, t, toast, getErrorMessage });
    useRouteState(kind, state);
    useRecoveryNotice(kind, state, t);
    useRegistrationTimer(kind, state);
    useQrCode(state);
}

function useSignInOptions(
    kind: AuthPageKind,
    state: AuthPageState,
    t: Translate,
    toast: ToastActions,
) {
    const setPrimaryMethods = state.setPrimaryMethods;
    useEffect(() => {
        if (kind === "google") notifyGoogleResult(t, toast);
        if (["login", "register", "google"].includes(kind)) {
            void authClient.get<{ primaryMethods: string[] }>("/sign-in-options")
                .then(options => {
                    setPrimaryMethods(options.primaryMethods.filter(isPrimarySignInMethod));
                })
                .catch(() => {
                    setPrimaryMethods([]);
                    toast.add({ title: t("signInOptionsUnavailable"), tone: "danger" });
                });
        }
    }, [kind, t, toast, setPrimaryMethods]);
}

function isPrimarySignInMethod(value: string): value is PrimarySignInMethod {
    return value === "PASSWORD" || value === "GOOGLE" || value === "EMAIL_SIGN_IN_CODE";
}

function notifyGoogleResult(t: Translate, toast: ToastActions) {
    const result = new URLSearchParams(window.location.search).get("error");
    if (result === "cancelled") {
        toast.add({ title: t("googleCancelled"), tone: "attention" });
    } else if (result) {
        toast.add({ title: t("googleFailed"), description: t("alternateSignInHelp"), tone: "danger" });
    }
}

function useSecuritySessions(options: {
    readonly kind: AuthPageKind;
    readonly state: AuthPageState;
    readonly t: Translate;
    readonly toast: ToastActions;
    readonly getErrorMessage: ErrorMessage;
}) {
    const { kind, state, t, toast, getErrorMessage } = options;
    const setSessions = state.setSessions;
    useEffect(() => {
        if (kind !== "security") return;
        void authClient.get<{
            id: string;
            device: string;
            createdAt: string;
            lastUsedAt: string;
            revokedAt: string | null;
        }[]>("/sessions")
            .then(setSessions)
            .catch((reason: unknown) => { toast.add({
                title: t("securityLoadFailed"),
                description: getErrorMessage(reason, t),
                tone: "danger",
            }); });
    }, [getErrorMessage, kind, setSessions, t, toast]);
}

function useRouteState(kind: AuthPageKind, state: AuthPageState) {
    const setAvailableMethods = state.setAvailableMethods;
    const setFactor = state.setFactor;
    const setOtpPurpose = state.setOtpPurpose;
    const setEmailSignInChallengeId = state.setEmailSignInChallengeId;
    const setRegistration = state.setRegistration;
    const setEmail = state.setEmail;
    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (active) initializeRouteState(kind, {
                setAvailableMethods, setFactor, setOtpPurpose,
                setEmailSignInChallengeId, setRegistration, setEmail,
            });
        });
        return () => { active = false; };
    }, [kind, setAvailableMethods, setFactor, setOtpPurpose,
        setEmailSignInChallengeId, setRegistration, setEmail]);
}

type RouteSetters = Pick<AuthPageState,
    "setAvailableMethods" | "setFactor" | "setOtpPurpose" | "setEmailSignInChallengeId" | "setRegistration" | "setEmail">;

function initializeRouteState(kind: AuthPageKind, setters: RouteSetters) {
    if (kind === "mfa") initializeMfaState(setters);
    const params = new URLSearchParams(window.location.search);
    const purpose = params.get("purpose");
    if (kind === "otp" && purpose === "registration") initializeRegistrationState(setters);
    else if (kind === "otp" && purpose === "login") {
        setters.setOtpPurpose("login");
        setters.setEmailSignInChallengeId("");
    }
}

function initializeMfaState(state: Pick<AuthPageState, "setAvailableMethods" | "setFactor">) {
    const params = new URLSearchParams(window.location.search);
    const pendingId = params.get("pending_id");
    if (pendingId) sessionStorage.setItem("tallyvane.pendingId", pendingId);
    const queryMethods = params.get("methods");
    const methods = (queryMethods?.split(",") ?? readStoredMethods()).filter(isSecondFactor);
    const recommended = params.get("recommended_method") ?? sessionStorage.getItem("tallyvane.recommendedMethod");
    state.setAvailableMethods(methods);
    if (recommended && methods.includes(recommended)) state.setFactor(recommended);
    else if (methods.length > 0) state.setFactor(methods[0] ?? "TOTP");
}

function readStoredMethods(): string[] {
    try {
        const value: unknown = JSON.parse(sessionStorage.getItem("tallyvane.availableMethods") ?? "[]");
        return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
    } catch {
        return [];
    }
}

function isSecondFactor(value: string): boolean {
    return value === "TOTP" || value === "EMAIL_OTP";
}

function useRecoveryNotice(kind: AuthPageKind, state: AuthPageState, t: Translate) {
    const setNotice = state.setNotice;
    useEffect(() => {
        if (kind === "security" && new URLSearchParams(window.location.search).get("recovered") === "1") {
            setNotice(t("recoveryCompletedNotice"));
        }
    }, [kind, setNotice, t]);
}

function initializeRegistrationState(state: Pick<AuthPageState, "setOtpPurpose" | "setRegistration" | "setEmail">) {
    state.setOtpPurpose("registration");
    try {
        const saved = sessionStorage.getItem("tallyvane.registration");
        if (!saved) return;
        const registration = JSON.parse(saved) as {
            challengeId?: string | null;
            email: string;
        };
        state.setRegistration(registration);
        state.setEmail(registration.email);
    } catch {
        sessionStorage.removeItem("tallyvane.registration");
    }
}

function useRegistrationTimer(kind: AuthPageKind, state: AuthPageState) {
    const { otpPurpose, registrationResendSeconds, setRegistrationResendSeconds } = state;
    useEffect(() => {
        if (kind !== "otp" || otpPurpose !== "registration" || registrationResendSeconds <= 0) return;
        const timer = window.setTimeout(() => {
            setRegistrationResendSeconds(seconds => Math.max(0, seconds - 1));
        }, 1000);
        return () => { window.clearTimeout(timer); };
    }, [kind, otpPurpose, registrationResendSeconds, setRegistrationResendSeconds]);
}

function useQrCode(state: AuthPageState) {
    const { payload, setQrCode } = state;
    useEffect(() => {
        if (!payload) return;
        let active = true;
        void QRCode.toDataURL(payload, {
            width: 240,
            margin: 1,
            errorCorrectionLevel: "M",
        }).then(image => {
            if (active) setQrCode(image);
        }).catch(() => {
            if (active) setQrCode("");
        });
        return () => { active = false; };
    }, [payload, setQrCode]);
}
