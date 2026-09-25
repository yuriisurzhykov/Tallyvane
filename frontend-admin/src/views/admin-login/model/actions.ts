import type { Dispatch, SyntheticEvent } from "react";
import {
    AdminAuthError,
    adminAuthClient,
    classifyAdminAccessStatus,
} from "@/features/admin-login";
import type { AdminFactor, SignInOutcome } from "@/features/admin-login";
import type { useAdminLoginStrings } from "@/features/admin-login";
import type { AdminLoginAction, AdminLoginState } from "./state";

type Translate = ReturnType<typeof useAdminLoginStrings>;
type DispatchAction = Dispatch<AdminLoginAction>;
type Navigate = (path: string) => void;

interface FlowContext {
    readonly state: AdminLoginState;
    readonly dispatch: DispatchAction;
    readonly navigate: Navigate;
    readonly returnTo: string;
    readonly t: Translate;
}

interface AccessContext {
    readonly dispatch: DispatchAction;
    readonly navigate: Navigate;
    readonly t: Translate;
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

export async function checkInitialAdminSession(
    dispatch: DispatchAction,
    navigate: Navigate,
    t: Translate,
    isActive: () => boolean,
) {
    try {
        const status = await adminAuthClient.checkAdminAccess();
        if (!isActive()) return;
        applyAccessResult(status, { dispatch, navigate, t, successPath: "/pages" });
    } catch (reason) {
        if (isActive()) patch(dispatch, { screen: "unavailable", error: errorMessage(reason, t, "general") });
    }
}

export async function retryAdminSession(dispatch: DispatchAction, navigate: Navigate, t: Translate) {
    patch(dispatch, { screen: "checking", error: "" });
    await checkInitialAdminSession(dispatch, navigate, t, () => true);
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
        await handleSignInOutcome(outcome, context);
    } catch (reason) {
        const message = reason instanceof AdminLoginFlowError ? reason.message : errorMessage(reason, t, "password");
        patch(dispatch, { error: message });
    } finally {
        patch(dispatch, { busy: false });
    }
}

async function handleSignInOutcome(
    outcome: SignInOutcome,
    context: FlowContext,
) {
    const { dispatch, t } = context;
    switch (outcome.status) {
        case "issued":
            await routeAfterIssuedSession({
                dispatch: context.dispatch,
                navigate: context.navigate,
                t: context.t,
                successPath: context.returnTo,
            });
            break;
        case "requires_second_factor":
            beginMfa(outcome, dispatch);
            break;
        case "requires_enrollment":
            await beginRequiredEnrollment(outcome, dispatch, t);
            break;
    }
}

function beginMfa(outcome: SignInOutcome, dispatch: DispatchAction) {
    if (!outcome.pendingId) throw new AdminLoginFlowError("The sign-in check expired. Please sign in again.");
    const methods = outcome.availableMethods?.filter(isAdminFactor) ?? [];
    patch(dispatch, {
        screen: "mfa",
        pendingId: outcome.pendingId,
        availableMethods: methods,
        factor: methods[0] ?? "TOTP",
        code: "",
        emailChallengeId: "",
        notice: "",
    });
}

async function beginRequiredEnrollment(outcome: SignInOutcome, dispatch: DispatchAction, t: Translate) {
    if (!outcome.pendingId) throw new AdminLoginFlowError(t("requestFailed"));
    if (outcome.availableMethods?.length && !outcome.availableMethods.includes("TOTP")) {
        throw new AdminLoginFlowError(t("requiredMethodUnavailable"));
    }
    patch(dispatch, { screen: "enrollment", pendingId: outcome.pendingId, otpauthUri: "", code: "" });
    const enrollment = await adminAuthClient.beginRequiredEnrollment(outcome.pendingId);
    patch(dispatch, { otpauthUri: enrollment.otpauthUri });
}

export async function submitAdminMfa(
    event: SyntheticEvent<HTMLFormElement>,
    context: FlowContext,
) {
    event.preventDefault();
    const { state, dispatch, navigate, returnTo, t } = context;
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
        await routeAfterIssuedSession({ dispatch, navigate, t, successPath: returnTo });
    } catch (reason) {
        const message = reason instanceof AdminLoginFlowError ? reason.message : errorMessage(reason, t, "verification");
        patch(dispatch, { error: message });
    } finally {
        patch(dispatch, { busy: false });
    }
}

export async function submitRequiredEnrollment(
    event: SyntheticEvent<HTMLFormElement>,
    state: AdminLoginState,
    dispatch: DispatchAction,
    t: Translate,
) {
    event.preventDefault();
    patch(dispatch, { busy: true, error: "" });
    try {
        await adminAuthClient.confirmRequiredEnrollment(state.pendingId, state.code);
        patch(dispatch, {
            screen: "password",
            pendingId: "",
            otpauthUri: "",
            code: "",
            notice: t("enrollmentComplete"),
        });
    } catch (reason) {
        patch(dispatch, { error: errorMessage(reason, t, "verification") });
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

async function routeAfterIssuedSession(context: AccessContext) {
    try {
        const status = await adminAuthClient.checkAdminAccess();
        applyAccessResult(status, context);
    } catch (reason) {
        patch(context.dispatch, { screen: "unavailable", error: errorMessage(reason, context.t, "general") });
    }
}

function applyAccessResult(status: number, context: AccessContext) {
    const result = classifyAdminAccessStatus(status);
    if (result === "authorized") context.navigate(context.successPath);
    else if (result === "unauthenticated") patch(context.dispatch, { screen: "password", error: context.t("requestFailed") });
    else if (result === "denied") patch(context.dispatch, { screen: "denied", error: context.t("accessDenied") });
    else patch(context.dispatch, { screen: "unavailable", error: context.t("requestFailed") });
}

function isAdminFactor(value: string): value is AdminFactor {
    return value === "TOTP" || value === "EMAIL_OTP" || value === "BACKUP_CODE";
}
