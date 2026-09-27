import { isSafeRelativePath } from "frontend-shared/lib";

export type AdminAccessStatus = "authorized" | "unauthenticated" | "denied" | "unavailable";

const ADMIN_RETURN_ROUTES = ["/pages", "/media", "/strings", "/authentication"] as const;

export function resolveAdminLoginReturnTo(value: string | null | undefined): string {
    if (!isSafeRelativePath(value)) return "/pages";
    const pathname = value.split("?", 1)[0] ?? "/";
    return ADMIN_RETURN_ROUTES.some(route => pathname === route || pathname.startsWith(`${route}/`))
        ? value
        : "/pages";
}

export function classifyAdminAccessStatus(status: number): AdminAccessStatus {
    if (status >= 200 && status < 300) return "authorized";
    if (status === 401) return "unauthenticated";
    if (status === 403) return "denied";
    return "unavailable";
}
