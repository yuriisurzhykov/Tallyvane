import type { Dispatch, SyntheticEvent } from "react";
import {
    AdminAuthError,
    adminAuthClient,
    resolveAdminLoginReturnTo,
} from "@/features/admin-login";
import type { AdminFactor, SignInOutcome } from "@/features/admin-login";
import type { useAdminLoginStrings } from "@/features/admin-login";
import type { AdminLoginAction, AdminLoginState } from "./state";

type Translate = ReturnType<typeof useAdminLoginStrings>;
type DispatchAction = Dispatch<AdminLoginAction>;

interface FlowContext {
    readonly state: AdminLoginState;
    readonly dispatch: DispatchAction;
    readonly returnTo: string;
    readonly t: Translate;
}

interface AccessContext {
    readonly successPath: string;
}

export class AdminLoginFlowError extends Error {
    public constructor(message: string) {
        super(message);
        this.name = "AdminLoginFlowError";
    }
}

function patch(dispatch: DispatchAction, value: Partial<AdminLoginState>) {
    dispatch({ type: "patch", patch: value });
}

function errorMessage(error: unknown, t: Translate, context: "password" | "verification" | "general") {
    if (!(error instanceof AdminAuthError)) return t("requestFailed");
    if (error.status === 0) return t("networkError");
    if (error.status === 401 || error.status === 400) {
        return context === "password" ? t("invalidCredentials") : t("invalidCode");
    }
    if (error.status === 403) return t("accessDenied");
    if (error.status === 429) return t("rateLimited");
    return t("requestFailed");
}

export async function submitAdminPassword(
    event: SyntheticEvent<HTMLFormElement>,
    context: FlowContext,
) {
    event.preventDefault();
    const { state, dispatch, t } = context;
    patch(dispatch, { busy: true, error: "" });
    try {
        const outcome = await adminAuthClient.signIn(state.email.trim(), state.password);
        patch(dispatch, { password: "" });
        handleSignInOutcome(outcome, context);
    } catch (reason) {
        const message = reason instanceof AdminLoginFlowError ? reason.message : errorMessage(reason, t, "password");
        patch(dispatch, { error: message });
    } finally {
        patch(dispatch, { busy: false });
    }
}

export async function submitAdminEmailCode(
    event: SyntheticEvent<HTMLFormElement>,
    context: FlowContext,
) {
    event.preventDefault();
    const { state, dispatch, t } = context;
    patch(dispatch, { busy: true, error: "" });
    try {
        if (!state.emailSignInChallengeId) {
            const result = await adminAuthClient.requestEmailSignInCode(state.email.trim());
            patch(dispatch, { emailSignInChallengeId: result.challengeId, code: "", notice: t("emailSignInCodeSent") });
            return;
        }
        const outcome = await adminAuthClient.verifyEmailSignInCode(
            state.emailSignInChallengeId,
            state.email.trim(),
            state.code,
        );
        handleSignInOutcome(outcome, context);
    } catch (reason) {
        const message = reason instanceof AdminLoginFlowError ? reason.message : errorMessage(reason, t, "verification");
        patch(dispatch, { error: message });
    } finally {
        patch(dispatch, { busy: false });
    }
}

function handleSignInOutcome(
    outcome: SignInOutcome,
    context: FlowContext,
): void {
    const { dispatch, t } = context;
    switch (outcome.status) {
        case "issued":
            routeAfterIssuedSession({
                successPath: context.returnTo,
            });
            break;
        case "requires_second_factor":
            beginMfa(outcome, dispatch, t);
            break;
    }
}

function beginMfa(outcome: SignInOutcome, dispatch: DispatchAction, t: Translate) {
    if (!outcome.pendingId) throw new AdminLoginFlowError(t("signInExpired"));
    const methods = outcome.availableMethods?.filter(isAdminFactor) ?? [];
    const recommended = outcome.recommendedMethod && methods.includes(outcome.recommendedMethod)
        ? outcome.recommendedMethod
        : undefined;
    if (!recommended || methods.length === 0) throw new AdminLoginFlowError(t("signInExpired"));
    patch(dispatch, {
        screen: "mfa",
        pendingId: outcome.pendingId,
        availableMethods: methods,
        factor: recommended,
        code: "",
        emailChallengeId: "",
        notice: "",
    });
}

export async function submitAdminMfa(
    event: SyntheticEvent<HTMLFormElement>,
    context: FlowContext,
) {
    event.preventDefault();
    const { state, dispatch, returnTo, t } = context;
    patch(dispatch, { busy: true, error: "" });
    try {
        if (state.factor === "EMAIL_OTP" && !state.emailChallengeId) {
            const result = await adminAuthClient.requestEmailFactorCode(state.pendingId);
            patch(dispatch, { emailChallengeId: result.challengeId, notice: t("emailCodeSent") });
            return;
        }
        const result = await adminAuthClient.verifyFactor(
            state.pendingId,
            state.factor,
            state.code,
            state.factor === "EMAIL_OTP" ? state.emailChallengeId : undefined,
        );
        if (result.status !== "issued") throw new AdminLoginFlowError(t("invalidCode"));
        routeAfterIssuedSession({ successPath: returnTo });
    } catch (reason) {
        const message = reason instanceof AdminLoginFlowError ? reason.message : errorMessage(reason, t, "verification");
        patch(dispatch, { error: message });
    } finally {
        patch(dispatch, { busy: false });
    }
}

export async function signOutDeniedAccount(dispatch: DispatchAction, t: Translate) {
    patch(dispatch, { busy: true, error: "" });
    try {
        await adminAuthClient.signOut();
        patch(dispatch, { screen: "password", notice: "" });
    } catch (reason) {
        patch(dispatch, { error: errorMessage(reason, t, "general") });
    } finally {
        patch(dispatch, { busy: false });
    }
}

function routeAfterIssuedSession(context: AccessContext) {
    const saved = sessionStorage.getItem("tallyvane.admin.auth.returnTo");
    sessionStorage.removeItem("tallyvane.admin.auth.returnTo");
    window.location.replace(resolveAdminLoginReturnTo(saved ?? context.successPath));
}

function isAdminFactor(value: string): value is AdminFactor {
    return value === "TOTP" || value === "EMAIL_OTP";
}
