import { useEffect, useReducer } from "react";
import { resolveAdminLoginReturnTo, useAdminLoginStrings } from "@/features/admin-login";
import type { AdminFactor } from "@/features/admin-login";
import type { AdminLoginState } from "./state";
import {
    signOutDeniedAccount,
    submitAdminEmailCode,
    submitAdminMfa,
    submitAdminPassword,
} from "./actions";
import { adminLoginReducer, initialAdminLoginState } from "./state";
import { adminAuthClient, adminSessionRuntime } from "@/features/admin-login";

export interface InitialAdminPendingAuthentication {
    readonly pendingId: string;
    readonly recommendedMethod: AdminFactor;
    readonly availableMethods: AdminFactor[];
}

export function useAdminLoginController(
    returnTo: string | null,
    initialPending?: InitialAdminPendingAuthentication,
    initialError?: "google",
) {
    const [state, dispatch] = useReducer(adminLoginReducer, initialAdminLoginState);
    const t = useAdminLoginStrings("adminLogin");
    const safeReturnTo = resolveAdminLoginReturnTo(returnTo);

    useEffect(() => {
        let active = true;
        const applySessionState = () => {
            const status = adminSessionRuntime.getSnapshot().status;
            if (status === "anonymous" && initialPending) {
                dispatch({ type: "patch", patch: {
                    screen: "mfa",
                    pendingId: initialPending.pendingId,
                    availableMethods: initialPending.availableMethods,
                    factor: initialPending.recommendedMethod,
                    error: "",
                } });
            } else if (status === "anonymous") {
                void adminAuthClient.readSignInOptions().then(methods => {
                    if (active) dispatch({ type: "patch", patch: {
                        screen: "password",
                        primaryMethods: methods,
                        signInMode: methods.includes("PASSWORD") ? "PASSWORD" : "EMAIL_SIGN_IN_CODE",
                        error: initialError === "google" ? t("googleSignInFailed") : "",
                    } });
                }).catch(() => {
                    if (active) dispatch({ type: "patch", patch: { screen: "unavailable", error: t("signInOptionsUnavailable") } });
                });
            }
            else if (status === "accessDenied") dispatch({ type: "patch", patch: { screen: "denied", error: t("accessDenied") } });
            else if (status === "unavailable") dispatch({ type: "patch", patch: { screen: "unavailable", error: t("networkError") } });
            else if (status === "checking" || status === "refreshing") dispatch({ type: "patch", patch: { screen: "checking" } });
        };
        const unsubscribe = adminSessionRuntime.subscribe(applySessionState);
        applySessionState();
        return () => { active = false; unsubscribe(); };
    }, [initialError, initialPending, t]);

    return {
        state,
        t,
        clearError: () => { dispatch({ type: "patch", patch: { error: "" } }); },
        setEmail: (email: string) => { dispatch({ type: "patch", patch: { email } }); },
        setPassword: (password: string) => { dispatch({ type: "patch", patch: { password } }); },
        setSignInMode: (signInMode: AdminLoginState["signInMode"]) => {
            dispatch({ type: "patch", patch: { signInMode, emailSignInChallengeId: "", code: "", error: "", notice: "" } });
        },
        setFactor: (factor: AdminLoginState["factor"]) => {
            dispatch({ type: "patch", patch: { factor, code: "", emailChallengeId: "", error: "", notice: "" } });
        },
        setCode: (code: string) => { dispatch({ type: "patch", patch: { code } }); },
        backToPassword: () => {
            dispatch({ type: "patch", patch: { screen: "password", pendingId: "", code: "", emailChallengeId: "", error: "", notice: "" } });
        },
        retry: () => { void adminSessionRuntime.verifySession(); },
        startGoogleSignIn: () => {
            sessionStorage.setItem("tallyvane.admin.auth.returnTo", safeReturnTo);
            window.location.href = "/api/v1/auth/admin/google/oauth/start";
        },
        submitPassword: (event: Parameters<typeof submitAdminPassword>[0]) => {
            void submitAdminPassword(event, { state, dispatch, returnTo: safeReturnTo, t });
        },
        submitEmailCode: (event: Parameters<typeof submitAdminEmailCode>[0]) => {
            void submitAdminEmailCode(event, { state, dispatch, returnTo: safeReturnTo, t });
        },
        submitMfa: (event: Parameters<typeof submitAdminMfa>[0]) => {
            void submitAdminMfa(event, { state, dispatch, returnTo: safeReturnTo, t });
        },
        signOut: () => { void signOutDeniedAccount(dispatch, t); },
    };
}

export type AdminLoginController = ReturnType<typeof useAdminLoginController>;
