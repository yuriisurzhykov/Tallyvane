import {
    AUTH_PROBLEM_TYPES,
    AuthSessionRuntime,
    createAuthSessionTransport,
    fromWireJson,
    toWireJson,
    type ApiProblem,
    type RefreshResult,
    type SessionProbeResult,
} from "frontend-shared/api";

export type AdminFactor = "TOTP" | "EMAIL_OTP";
export type AdminPrimarySignInMethod = "PASSWORD" | "GOOGLE" | "EMAIL_SIGN_IN_CODE";
export type AdminAccountAction = "CHANGE_PRIMARY_CREDENTIAL" | "MANAGE_SECOND_FACTORS";
export type AdminProofTokenKind = "PASSWORD" | "GOOGLE" | "EMAIL_SIGN_IN_CODE" | "TOTP" | "EMAIL_FACTOR_CODE";
export interface AdminProofScheme {
    readonly id: string;
    readonly requiredTokens: AdminProofTokenKind[];
    readonly assuranceRank: number;
}
export interface AdminPresentedProofToken {
    readonly kind: AdminProofTokenKind;
    readonly value: string;
    readonly challengeId?: string;
    readonly codeVerifier?: string;
    readonly redirectUri?: string;
}

export interface SignInOutcome {
    readonly status: "issued" | "requires_second_factor";
    readonly pendingId?: string;
    readonly recommendedMethod?: AdminFactor;
    readonly availableMethods?: AdminFactor[];
}

export class AdminAuthError extends Error {
    public constructor(public readonly status: number, public readonly problem?: ApiProblem) {
        super(`Admin authentication request failed with status ${String(status)}`);
        this.name = "AdminAuthError";
    }
}

export function createAdminAuthClient(fetcher: typeof fetch = fetch) {
    async function request<T>(path: string, method = "GET", body?: unknown): Promise<T> {
        const headers = new Headers({ Accept: "application/json" });
        if (method !== "GET") {
            const csrfResponse = await send("/csrf", "GET");
            if (!csrfResponse.ok) throw new AdminAuthError(csrfResponse.status);
            const csrf = await csrfResponse.json() as { token?: string };
            if (!csrf.token) throw new AdminAuthError(403);
            headers.set("X-CSRF-Token", csrf.token);
            headers.set("Content-Type", "application/json");
        }

        const response = await send(path, method, headers, body);
        if (!response.ok) throw new AdminAuthError(response.status, await readProblem(response));
        if (response.status === 204) return undefined as T;
        return fromWireJson(await response.json()) as T;
    }

    async function send(path: string, method: string, headers?: Headers, body?: unknown): Promise<Response> {
        try {
            const endpoint = path === "/csrf"
                ? `/api/v1/auth${path}`
                : `/api/v1/auth/admin${path}`;
            return await fetcher(endpoint, {
                method,
                headers: headers ?? { Accept: "application/json" },
                credentials: "same-origin",
                cache: "no-store",
                ...(body === undefined ? {} : { body: JSON.stringify(toWireJson(body)) }),
            });
        } catch {
            throw new AdminAuthError(0);
        }
    }

    return {
        requestJson: <T>(path: string, method = "GET", body?: unknown) => request<T>(path, method, body),
        checkAdminAccess: () => send("/policy", "GET").then(response => response.status),
        probeAdminAccess: async () => {
            const response = await send("/policy", "GET");
            return { status: response.status, problem: await readProblem(response) };
        },
        readActionSchemes: async (action: AdminAccountAction) => {
            const result = await request<{ schemes: { id: string; requiredTokens: AdminProofTokenKind[]; assuranceRank: number }[] }>(
                `/account/action-proof/options?action=${encodeURIComponent(action)}`,
            );
            return result.schemes.map(scheme => ({
                id: scheme.id,
                requiredTokens: scheme.requiredTokens,
                assuranceRank: scheme.assuranceRank,
            } satisfies AdminProofScheme));
        },
        requestActionEmailCode: async (action: AdminAccountAction, kind: "EMAIL_SIGN_IN_CODE" | "EMAIL_FACTOR_CODE") => {
            const result = await request<{ challengeId: string }>("/account/action-proof/email-code", "POST", { action, kind });
            return { challengeId: result.challengeId };
        },
        startGoogleActionProof: (action: AdminAccountAction) => request<{ url: string }>("/google/proof/start", "POST", { action }),
        issueActionProof: (action: AdminAccountAction, tokens: AdminPresentedProofToken[]) => request<{ proof: string }>(
            "/account/action-proof", "POST", {
                action,
                tokens: tokens.map(token => ({
                    kind: token.kind,
                    value: token.value,
                    ...(token.challengeId ? { challengeId: token.challengeId } : {}),
                    ...(token.codeVerifier ? { codeVerifier: token.codeVerifier } : {}),
                    ...(token.redirectUri ? { redirectUri: token.redirectUri } : {}),
                })),
            },
        ),
        signIn: (email: string, password: string) =>
            request<SignInOutcome>("/login/password", "POST", { email, password, device: "Admin browser" }),
        requestEmailSignInCode: (email: string) => request<{ challengeId: string }>("/login/email/code", "POST", { email }),
        verifyEmailSignInCode: (challengeId: string, email: string, code: string) =>
            request<SignInOutcome>("/login/email/verify", "POST", { challengeId, email, code, device: "Admin browser" }),
        readSignInOptions: () => request<{ primaryMethods: AdminPrimarySignInMethod[] }>("/sign-in-options")
            .then(result => result.primaryMethods),
        requestEmailFactorCode: (pendingId: string) => request<{ challengeId: string }>("/mfa/email/request", "POST", { pendingId }),
        verifyFactor: (pendingId: string, kind: AdminFactor, code: string, challengeId?: string) =>
            request<{ status: string }>("/mfa/verify", "POST", {
                pendingId,
                kind,
                code,
                ...(challengeId ? { challengeId } : {}),
            }),
        signOut: () => request<undefined>("/logout", "POST"),
        refreshSession: () => request<{ status: string }>("/refresh", "POST"),
    };
}

async function readProblem(response: Response): Promise<ApiProblem | undefined> {
    if (response.ok) return undefined;
    const body = fromWireJson(await response.clone().json().catch(() => undefined));
    if (!body || typeof body !== "object" || typeof (body as Record<string, unknown>).type !== "string") return undefined;
    return body as ApiProblem;
}

const rawAdminAuthClient = createAdminAuthClient();

async function checkAdminSession(): Promise<SessionProbeResult> {
    try {
        const result = await rawAdminAuthClient.probeAdminAccess();
        if (result.status >= 200 && result.status < 300) return { status: "authenticated" };
        if (result.status === 401 && result.problem?.type === AUTH_PROBLEM_TYPES.unauthorized) {
            return { status: "unauthorized" };
        }
        if (result.status === 403 && result.problem?.type === AUTH_PROBLEM_TYPES.forbidden) {
            return { status: "accessDenied", problem: result.problem };
        }
        return { status: "unavailable" };
    } catch {
        return { status: "unavailable" };
    }
}

async function refreshAdminSession(): Promise<RefreshResult> {
    try {
        await rawAdminAuthClient.refreshSession();
        return "refreshed";
    } catch (reason) {
        if (reason instanceof AdminAuthError && reason.status === 401 &&
            reason.problem?.type === AUTH_PROBLEM_TYPES.unauthorized) return "rejected";
        return "unavailable";
    }
}

export const adminSessionRuntime = new AuthSessionRuntime({
    checkSession: checkAdminSession,
    refreshSession: refreshAdminSession,
    lockName: "tallyvane.admin.auth.refresh",
});

export const adminAuthClient = createAdminAuthClient(createAuthSessionTransport(adminSessionRuntime));
