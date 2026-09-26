"use client";

import { useCallback, useEffect, useRef } from "react";
import { usePathname, useRouter } from "next/navigation";
import { AuthSessionBoundary } from "frontend-shared/auth-session";
import { isSafeRelativePath } from "frontend-shared/lib";
import { Button } from "frontend-shared/ui/button";
import { Text } from "frontend-shared/ui/text";
import { useAdminLoginStrings, adminSessionRuntime } from "@/features/admin-login";
import type { AuthSessionState } from "frontend-shared/api";
import { AdminStepUpDialog } from "./AdminStepUpDialog";
import styles from "./AdminSessionGate.module.css";

function classifyRoute(pathname: string) {
    return pathname === "/login" ? "guest" as const : "protected" as const;
}

function safeReturnTo(value: string | null, fallback = "/pages") {
    return isSafeRelativePath(value) && value !== "/login" && !value.startsWith("/login?") ? value : fallback;
}

function loginLocation(currentLocation: string) {
    const target = typeof window === "undefined" ? currentLocation : `${window.location.pathname}${window.location.search}`;
    return `/login?returnTo=${encodeURIComponent(safeReturnTo(target))}`;
}

function authenticatedLocation() {
    const returnTo = typeof window === "undefined" ? null : new URLSearchParams(window.location.search).get("returnTo");
    return safeReturnTo(returnTo);
}

export function AdminSessionGate({ children }: { readonly children: React.ReactNode }) {
    const pathname = usePathname() ?? "/";
    const router = useRouter();
    const t = useAdminLoginStrings("adminLogin");
    const navigate = useCallback((href: string) => router.replace(href), [router]);
    const onVerified = useCallback((location: string, state: AuthSessionState, reason: "route" | "resume" | "focus") => {
        if (classifyRoute(location) !== "protected" || state.status !== "authenticated") return;
        if (location === "/") {
            router.replace("/pages");
            return;
        }
        if (reason !== "focus") router.refresh();
    }, [router]);
    const renderStatus = useCallback((state: AuthSessionState, retry: () => void) => {
        if (state.status === "stepUpRequired") return <AdminStepUpDialog problem={state.problem} />;
        if (state.status === "accessDenied" && state.target) return <AdminActionDeniedDialog t={t} />;
        const title = state.status === "accessDenied" ? t("accessDeniedTitle") :
            state.status === "unavailable" ? t("networkError") : t("checkingSession");
        return <main className={styles.statusPage} role="status" aria-live="polite">
            <section className={styles.statusPanel}>
                <Text as="h1" variant="title2">{title}</Text>
                {state.status === "accessDenied" && <Text as="p" variant="body">{t("accessDenied")}</Text>}
                {state.status === "unavailable" && <Button type="button" tone="neutral" onClick={retry}>{t("retry")}</Button>}
                {state.status === "accessDenied" && <Button type="button" tone="neutral"
                    onClick={() => navigate(loginLocation(pathname))}>{t("tryAnotherAccount")}</Button>}
            </section>
        </main>;
    }, [navigate, pathname, t]);

    return <AuthSessionBoundary
        runtime={adminSessionRuntime}
        location={pathname}
        classifyRoute={classifyRoute}
        loginLocation={loginLocation}
        authenticatedLocation={authenticatedLocation}
        navigate={navigate}
        renderStatus={renderStatus}
        renderGuestOnDenied
        onVerified={onVerified}
    >{children}</AuthSessionBoundary>;
}

function AdminActionDeniedDialog({ t }: { readonly t: ReturnType<typeof useAdminLoginStrings> }) {
    const router = useRouter();
    const dialog = useRef<HTMLDialogElement>(null);
    useEffect(() => {
        const element = dialog.current;
        if (!element) return;
        if (!element.open) element.showModal();
        return () => { if (element.open) element.close(); };
    }, []);
    return <dialog ref={dialog} className={styles.stepUpDialog} aria-labelledby="admin-action-denied-title"
        onCancel={event => event.preventDefault()}>
        <section className={styles.stepUpPanel}>
            <Text as="h1" variant="title2" id="admin-action-denied-title">{t("accessDeniedTitle")}</Text>
            <Text as="p" variant="body">{t("actionAccessDenied")}</Text>
            <Button type="button" tone="neutral" onClick={() => {
                adminSessionRuntime.completeAccessDenied();
                router.refresh();
            }}>{t("close")}</Button>
        </section>
    </dialog>;
}
