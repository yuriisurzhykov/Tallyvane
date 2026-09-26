import { useCallback, useEffect, useReducer } from "react";
import { useRouter } from "next/navigation";
import { resolveAdminLoginReturnTo, useAdminLoginStrings } from "@/features/admin-login";
import type { AdminLoginState } from "./state";
import {
    signOutDeniedAccount,
    submitAdminMfa,
    submitAdminPassword,
    submitRequiredEnrollment,
} from "./actions";
import { adminLoginReducer, initialAdminLoginState } from "./state";
import { adminSessionRuntime } from "@/features/admin-login";

export function useAdminLoginController(returnTo: string | null) {
    const [state, dispatch] = useReducer(adminLoginReducer, initialAdminLoginState);
    const router = useRouter();
    const t = useAdminLoginStrings("adminLogin");
    const safeReturnTo = resolveAdminLoginReturnTo(returnTo);
    const navigate = useCallback((path: string) => { router.replace(path); }, [router]);

    useEffect(() => {
        const applySessionState = () => {
            const status = adminSessionRuntime.getSnapshot().status;
            if (status === "anonymous") dispatch({ type: "patch", patch: { screen: "password", error: "" } });
            else if (status === "accessDenied") dispatch({ type: "patch", patch: { screen: "denied", error: t("accessDenied") } });
            else if (status === "unavailable") dispatch({ type: "patch", patch: { screen: "unavailable", error: t("networkError") } });
            else if (status === "checking" || status === "refreshing") dispatch({ type: "patch", patch: { screen: "checking" } });
        };
        const unsubscribe = adminSessionRuntime.subscribe(applySessionState);
        applySessionState();
        return unsubscribe;
    }, [t]);

    return {
        state,
        t,
        clearError: () => { dispatch({ type: "patch", patch: { error: "" } }); },
        setEmail: (email: string) => { dispatch({ type: "patch", patch: { email } }); },
        setPassword: (password: string) => { dispatch({ type: "patch", patch: { password } }); },
        setFactor: (factor: AdminLoginState["factor"]) => {
            dispatch({ type: "patch", patch: { factor, code: "", emailChallengeId: "", error: "", notice: "" } });
        },
        setCode: (code: string) => { dispatch({ type: "patch", patch: { code } }); },
        backToPassword: () => {
            dispatch({ type: "patch", patch: { screen: "password", pendingId: "", code: "", emailChallengeId: "", error: "", notice: "" } });
        },
        retry: () => { void adminSessionRuntime.verifySession(); },
        submitPassword: (event: Parameters<typeof submitAdminPassword>[0]) => {
            void submitAdminPassword(event, { state, dispatch, navigate, returnTo: safeReturnTo, t });
        },
        submitMfa: (event: Parameters<typeof submitAdminMfa>[0]) => {
            void submitAdminMfa(event, { state, dispatch, navigate, returnTo: safeReturnTo, t });
        },
        submitEnrollment: (event: Parameters<typeof submitRequiredEnrollment>[0]) => {
            void submitRequiredEnrollment(event, state, dispatch, t);
        },
        signOut: () => { void signOutDeniedAccount(dispatch, t); },
    };
}

export type AdminLoginController = ReturnType<typeof useAdminLoginController>;
