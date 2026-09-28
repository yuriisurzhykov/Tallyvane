import { AuthError } from "../api/client";
import type { AuthStringKey } from "../model/strings";

const ERROR_STATUS_MAP: Record<number, AuthStringKey> = {
    0: "networkError",
    401: "sessionExpiredError",
    403: "securitySessionError",
    429: "rateLimitedError",
};

export function parseAuthError(error: unknown, t: (key: AuthStringKey) => string): string {
    if (!(error instanceof AuthError)) return t("requestFailed");
    const key = ERROR_STATUS_MAP[error.status] ?? "requestFailed";
    return t(key);
}