export class AuthError extends Error {
    constructor(message: string, readonly status: number, readonly retryAfter = 0) {
        super(message);
        this.name = "AuthError";
    }
}

export type Factor = "email" | "totp" | "backup";
export interface AuthResult {
    status: "issued" | "requires_second_factor" | "requires_email_verification" | "requires_enrollment";
    pendingId?: string;
    challengeId?: string;
    availableMethods?: Factor[];
    requiredMethods?: Factor[];
    primaryMethod?: string;
}
export interface Enrollment { payload?: string; secret?: string; otpauthUri?: string; backupCodes?: string[] }
export interface Session { id: string; createdAt?: string; lastSeenAt?: string; expiresAt?: string; device?: string | { name?: string }; current?: boolean }
export interface Security { email: string; emailVerified: boolean; passwordEnabled: boolean; methods: { kind: Factor; enabled: boolean }[]; sessions?: Session[] }

export function createAuthClient(fetcher: typeof fetch = fetch) {
    async function request<T>(path: string, method: string, body?: unknown): Promise<T> {
        const headers = new Headers({ Accept: "application/json" });
        if (method !== "GET") {
            const csrf = await request<{ token: string }>("/csrf", "GET");
            if (!csrf.token) throw new AuthError("Your security session could not be started. Refresh and try again.", 403);
            headers.set("X-CSRF-Token", csrf.token);
            headers.set("Content-Type", "application/json");
        }
        let response: Response;
        try {
            response = await fetcher(`/api/v1/auth${path}`, {
                method, headers, credentials: "same-origin", cache: "no-store",
                ...(body === undefined ? {} : { body: JSON.stringify(body) }),
            });
        } catch {
            throw new AuthError("We couldn’t reach the server. Check your connection and try again.", 0);
        }
        const data: unknown = response.status === 204 ? undefined : await response.json().catch(() => undefined);
        if (!response.ok) {
            const detail = data && typeof data === "object" && "detail" in data && typeof data.detail === "string" ? data.detail : undefined;
            const retryAfter = Number(response.headers.get("Retry-After")) || 0;
            throw new AuthError(detail ?? (response.status === 401 ? "Your session has expired. Sign in again." : "This request could not be completed. Please try again."), response.status, retryAfter);
        }
        return data as T;
    }
    return {
        get: <T>(path: string) => request<T>(path, "GET"),
        post: <T>(path: string, body?: unknown) => request<T>(path, "POST", body),
        remove: <T>(path: string) => request<T>(path, "DELETE"),
    };
}
export const authClient = createAuthClient();
