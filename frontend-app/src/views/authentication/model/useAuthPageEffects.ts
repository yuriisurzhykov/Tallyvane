import { useEffect } from "react";
import QRCode from "qrcode";
import { authClient } from "../../../features/authentication/api/client";
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
    useProviderStatus(kind, state, t, toast);
    useSecuritySessions({ kind, state, t, toast, getErrorMessage });
    useRouteState(kind, state);
    useRegistrationTimer(kind, state);
    useQrCode(state);
}

function useProviderStatus(
    kind: AuthPageKind,
    state: AuthPageState,
    t: Translate,
    toast: ToastActions,
) {
    const setGoogleEnabled = state.setGoogleEnabled;
    useEffect(() => {
        if (kind === "google") notifyGoogleResult(t, toast);
        if (["login", "register", "google"].includes(kind)) {
            void fetch("/api/v1/auth/providers", { credentials: "same-origin", cache: "no-store" })
                .then(response => response.ok ? response.json() as Promise<{ google?: boolean }> : null)
                .then(providers => { setGoogleEnabled(providers?.google === true); })
                .catch(() => { setGoogleEnabled(false); });
        }
    }, [kind, t, toast, setGoogleEnabled]);
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
    const factor = state.factor;
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
                factor, setAvailableMethods, setFactor, setOtpPurpose,
                setEmailSignInChallengeId, setRegistration, setEmail,
            });
        });
        return () => { active = false; };
    }, [kind, factor, setAvailableMethods, setFactor, setOtpPurpose,
        setEmailSignInChallengeId, setRegistration, setEmail]);
}

type RouteSetters = Pick<AuthPageState,
    "factor" | "setAvailableMethods" | "setFactor" | "setOtpPurpose" | "setEmailSignInChallengeId" | "setRegistration" | "setEmail">;

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

function initializeMfaState(state: Pick<AuthPageState, "factor" | "setAvailableMethods" | "setFactor">) {
    const params = new URLSearchParams(window.location.search);
    const pendingId = params.get("pending_id");
    if (pendingId) sessionStorage.setItem("tallyvane.pendingId", pendingId);
    const queryMethods = params.get("methods");
    const storedMethods = sessionStorage.getItem("tallyvane.availableMethods");
    const methods = queryMethods?.split(",").filter(Boolean) ?? JSON.parse(storedMethods ?? "[]") as string[];
    state.setAvailableMethods(methods);
    if (methods.length > 0 && !methods.includes(state.factor)) state.setFactor(methods[0] ?? "TOTP");
}

function initializeRegistrationState(state: Pick<AuthPageState, "setOtpPurpose" | "setRegistration" | "setEmail">) {
    state.setOtpPurpose("registration");
    try {
        const saved = sessionStorage.getItem("tallyvane.registration");
        if (!saved) return;
        const registration = JSON.parse(saved) as {
            userId: string;
            challengeId: string | null;
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
