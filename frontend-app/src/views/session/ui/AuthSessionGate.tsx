"use client";

import { useCallback, useEffect, useRef } from "react";
import { usePathname, useRouter } from "next/navigation";
import { AuthSessionBoundary } from "frontend-shared/auth-session";
import { isSafeRelativePath } from "frontend-shared/lib";
import { Button } from "frontend-shared/ui/button";
import { Text } from "frontend-shared/ui/text";
import { authSessionRuntime } from "@/features/authentication/api/client";
import { useAuthStrings } from "@/features/authentication/model/strings";
import type { AuthSessionState } from "frontend-shared/api";
import type { AccountAction } from "@/features/authentication/api/actionProof";
import { AuthenticationActionForm } from "@/widgets/authentication-step/ui/AuthenticationActionForm";
import styles from "./AuthSessionGate.module.css";

const guestRoutes = ["/login", "/register", "/forgot-password", "/otp", "/mfa", "/mfa/enroll"];
const publicRoutes = ["/auth-preview", "/auth/google", "/auth/callback"];
const defaultHome = "/today";

function isAtRoute(pathname: string, route: string) {
    return pathname === route || pathname.startsWith(`${ route }/`);
}

function classifyRoute(pathname: string) {
    if (publicRoutes.some(route => isAtRoute(pathname, route))) return "public" as const;
    if (guestRoutes.some(route => isAtRoute(pathname, route))) return "guest" as const;
    return "protected" as const;
}

function safeDestination(value: string | null) {
    if (!isSafeRelativePath(value)) return defaultHome;
    const pathname = value.split("?", 1)[0] ?? "/";
    return guestRoutes.some(route => isAtRoute(pathname, route)) ? defaultHome : value;
}

function createLoginLocation(current: string) {
    const returnTo = typeof window === "undefined" ? current : `${ window.location.pathname }${ window.location.search }`;
    return `/login?returnTo=${ encodeURIComponent(safeDestination(returnTo)) }`;
}

function createAuthenticatedLocation() {
    const returnTo = typeof window === "undefined" ? null : new URLSearchParams(window.location.search).get("returnTo");
    return safeDestination(returnTo);
}

export function AuthSessionGate({ children }: { readonly children: React.ReactNode }) {
    const pathname = usePathname();
    const router = useRouter();
    const t = useAuthStrings("auth");
    const navigate = useCallback((href: string) => {
        router.replace(href)
    }, [router]);
    const onVerified = useCallback((location: string, state: AuthSessionState, reason: "route" | "resume" | "focus") => {
        if (classifyRoute(location) !== "protected" || state.status !== "authenticated") return;
        if (location === "/") {
            router.replace(defaultHome);
            return;
        }
        if (reason !== "focus") router.refresh();
    }, [router]);

    const renderStatus = useCallback((state: AuthSessionState, retry: () => void) => {
        if (state.status === "stepUpRequired") {
            return <StepUpDialog state={ state } t={ t }/>;
        }
        if (state.status === "accessDenied" && state.target) return <ActionDeniedDialog t={ t }/>;
        const title = state.status === "accessDenied" ? t("accessDenied") :
            state.status === "unavailable" ? t("networkError") : t("checkingSession");
        return <main className={ styles.statusPage } role="status" aria-live="polite">
            <section className={ styles.statusPanel }>
                <Text as="h1" variant="title2">{ title }</Text>
                { state.status === "unavailable" &&
                    <Button type="button" tone="neutral" onClick={ retry }>{ t("retry") }</Button> }
            </section>
        </main>;
    }, [t]);

    return <AuthSessionBoundary
        runtime={ authSessionRuntime }
        location={ pathname }
        classifyRoute={ classifyRoute }
        loginLocation={ createLoginLocation }
        authenticatedLocation={ createAuthenticatedLocation }
        navigate={ navigate }
        renderStatus={ renderStatus }
        onVerified={ onVerified }
    >{ children }</AuthSessionBoundary>;
}

function ActionDeniedDialog({ t }: { readonly t: ReturnType<typeof useAuthStrings> }) {
    const dialog = useRef<HTMLDialogElement>(null);
    useEffect(() => {
        const element = dialog.current;
        if (!element) return;
        if (!element.open) element.showModal();
        return () => {
            if (element.open) element.close();
        };
    }, []);
    return <dialog ref={ dialog } className={ styles.stepUpDialog } aria-labelledby="action-denied-title"
                   onCancel={ event => event.preventDefault() }>
        <section className={ styles.stepUpPanel }>
            <Text as="h1" variant="title2" id="action-denied-title">{ t("accessDenied") }</Text>
            <Text as="p" variant="body">{ t("actionAccessDeniedDescription") }</Text>
            <Button type="button" tone="neutral"
                    onClick={ () => authSessionRuntime.completeAccessDenied() }>{ t("dismiss") }</Button>
        </section>
    </dialog>;
}

function StepUpDialog({ state, t }: {
    readonly state: Extract<AuthSessionState, { status: "stepUpRequired" }>;
    readonly t: ReturnType<typeof useAuthStrings>;
}) {
    const dialog = useRef<HTMLDialogElement>(null);
    const action: AccountAction | null = state.problem.action === "CHANGE_PRIMARY_CREDENTIAL" ||
    state.problem.action === "MANAGE_SECOND_FACTORS" ? state.problem.action : null;

    useEffect(() => {
        const element = dialog.current;
        if (!element) return;
        if (!element.open) element.showModal();
        return () => {
            if (element.open) element.close();
        };
    }, []);

    return <dialog ref={ dialog } className={ styles.stepUpDialog } aria-labelledby="step-up-title"
                   onCancel={ event => event.preventDefault() }>
        <section className={ styles.stepUpPanel }>
            <Text as="h1" variant="title2" id="step-up-title">{ t("stepUpDialogTitle") }</Text>
            <Text as="p" variant="body">{ t("stepUpDialogDescription") }</Text>
            { action ? <AuthenticationActionForm
                action={ action }
                t={ t }
                submitLabel={ t("continue") }
                onAuthorized={ proof => authSessionRuntime.completeStepUp(proof) }
            /> : <Text as="p" variant="body" role="alert">{ t("reauthFailed") }</Text> }
        </section>
    </dialog>;
}
