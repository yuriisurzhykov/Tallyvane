export type AdminFactor = "TOTP" | "EMAIL_OTP" | "BACKUP_CODE";

export interface SignInOutcome {
    readonly status: "issued" | "requires_second_factor" | "requires_enrollment";
    readonly pendingId?: string;
    readonly availableMethods?: AdminFactor[];
}

export class AdminAuthError extends Error {
    public constructor(public readonly status: number) {
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
        if (!response.ok) throw new AdminAuthError(response.status);
        if (response.status === 204) return undefined as T;
        return await response.json() as T;
    }

    async function send(path: string, method: string, headers?: Headers, body?: unknown): Promise<Response> {
        try {
            return await fetcher(`/api/v1/auth${path}`, {
                method,
                headers: headers ?? { Accept: "application/json" },
                credentials: "same-origin",
                cache: "no-store",
                ...(body === undefined ? {} : { body: JSON.stringify(body) }),
            });
        } catch {
            throw new AdminAuthError(0);
        }
    }

    return {
        checkAdminAccess: () => send("/admin/policy", "GET").then(response => response.status),
        signIn: (email: string, password: string) => request<SignInOutcome>("/login/password", "POST", {
            email,
            password,
            device: "Admin browser",
        }),
        requestEmailFactorCode: (pendingId: string) => request<{ challengeId: string }>("/mfa/email/request", "POST", { pendingId }),
        verifyFactor: (pendingId: string, kind: AdminFactor, code: string, challengeId?: string) =>
            request<{ status: string }>("/mfa/verify", "POST", {
                pendingId,
                kind,
                code,
                ...(challengeId ? { challengeId } : {}),
            }),
        beginRequiredEnrollment: (pendingId: string) => request<{ otpauthUri: string }>("/mfa/required/enroll", "POST", {
            pendingId,
            kind: "TOTP",
        }),
        confirmRequiredEnrollment: (pendingId: string, code: string) => request<undefined>("/mfa/required/confirm", "POST", {
            pendingId,
            kind: "TOTP",
            code,
        }),
        signOut: () => request<undefined>("/logout", "POST"),
    };
}

export const adminAuthClient = createAdminAuthClient();
