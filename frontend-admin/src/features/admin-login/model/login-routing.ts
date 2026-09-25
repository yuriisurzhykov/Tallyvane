import { isSafeRelativePath } from "frontend-shared/lib";

export type AdminAccessStatus = "authorized" | "unauthenticated" | "denied" | "unavailable";

export function resolveAdminLoginReturnTo(value: string | null | undefined): string {
    return isSafeRelativePath(value) ? value : "/pages";
}

export function classifyAdminAccessStatus(status: number): AdminAccessStatus {
    if (status >= 200 && status < 300) return "authorized";
    if (status === 401) return "unauthenticated";
    if (status === 403) return "denied";
    return "unavailable";
}
