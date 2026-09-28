"use client";

import { useCallback, useEffect, useState, useSyncExternalStore, type ReactNode } from "react";
import type { AuthSessionRuntime, AuthSessionState } from "../api/auth-session-runtime";
import { Stack } from "../ui/stack";

export type SessionRouteKind = "protected" | "guest" | "public";
const SERVER_SNAPSHOT: AuthSessionState = { status: "checking" };

export interface AuthSessionBoundaryProps {
    readonly runtime: AuthSessionRuntime;
    readonly location: string;
    readonly classifyRoute: (pathname: string) => SessionRouteKind;
    readonly loginLocation: (currentLocation: string) => string;
    readonly authenticatedLocation: (currentLocation: string) => string;
    readonly navigate: (href: string) => void;
    readonly renderStatus: (state: AuthSessionState, retry: () => void) => ReactNode;
    readonly onVerified?: (location: string, state: AuthSessionState, reason: "route" | "resume" | "focus") => void;
    readonly renderGuestOnDenied?: boolean;
    readonly children: ReactNode;
}

/**
 * Shared route gate for applications using the session runtime. Routing details are injected by
 * each Next app, while verification, stale-route blocking, and auth-state handling stay common.
 */
export function AuthSessionBoundary({
    runtime,
    location,
    classifyRoute,
    loginLocation,
    authenticatedLocation,
    navigate,
    renderStatus,
    onVerified,
    renderGuestOnDenied = false,
    children,
}: AuthSessionBoundaryProps) {
    const routeKind = classifyRoute(location.split("?", 1)[0] ?? "/");
    const { sessionState, routeVerified } = useSessionVerification({ runtime, location, routeKind, onVerified });

    useEffect(() => {
        if (!routeVerified || sessionState.status === "checking" || sessionState.status === "refreshing") return;
        if (routeKind === "protected" && sessionState.status === "anonymous") {
            navigate(loginLocation(location));
        } else if (routeKind === "guest" && sessionState.status === "authenticated") {
            navigate(authenticatedLocation(location));
        }
    }, [authenticatedLocation, loginLocation, location, navigate, routeKind, routeVerified, sessionState]);

    return <SessionContent runtime={runtime} routeKind={routeKind} sessionState={sessionState}
        routeVerified={routeVerified} renderStatus={renderStatus} renderGuestOnDenied={renderGuestOnDenied}>
        {children}
    </SessionContent>;
}

function SessionContent({ runtime, routeKind, sessionState, routeVerified, renderStatus,
    renderGuestOnDenied, children }: Pick<AuthSessionBoundaryProps, "runtime" | "renderStatus" | "children"> & {
        routeKind: SessionRouteKind;
        sessionState: AuthSessionState;
        routeVerified: boolean;
        renderGuestOnDenied: boolean;
    }) {
    const retry = () => { void runtime.verifySession(); };
    if (sessionState.status === "stepUpRequired" || (sessionState.status === "accessDenied" && sessionState.target)) {
        return <><Stack gap="stack" inert aria-hidden>{children}</Stack>{renderStatus(sessionState, retry)}</>;
    }
    if (routeKind === "public") return children;
    if (sessionIsPending(routeVerified, sessionState)) {
        return renderStatus({ status: "checking" }, retry);
    }
    if (routeKind === "protected" && sessionState.status === "anonymous") {
        return renderStatus(sessionState, retry);
    }
    if (routeKind === "guest" && sessionState.status === "authenticated") {
        return renderStatus({ status: "checking" }, retry);
    }
    if (routeKind === "guest" && sessionState.status === "accessDenied" && renderGuestOnDenied) return children;
    if (sessionState.status === "accessDenied" || sessionState.status === "unavailable") {
        return renderStatus(sessionState, retry);
    }
    return children;
}

function sessionIsPending(routeVerified: boolean, state: AuthSessionState): boolean {
    return !routeVerified || state.status === "checking" || state.status === "refreshing";
}

function useSessionVerification({ runtime, location, routeKind, onVerified }: {
    runtime: AuthSessionRuntime;
    location: string;
    routeKind: SessionRouteKind;
    onVerified: AuthSessionBoundaryProps["onVerified"];
}) {
    const [verifiedLocation, setVerifiedLocation] = useState<string | null>(null);
    const sessionState = useSyncExternalStore(
        runtime.subscribe,
        runtime.getSnapshot,
        () => SERVER_SNAPSHOT,
    );

    useEffect(() => {
        let active = true;
        if (routeKind === "public") {
            setVerifiedLocation(location);
            return () => { active = false; };
        }

        setVerifiedLocation(null);
        void runtime.verifySession().finally(() => {
            if (!active) return;
            setVerifiedLocation(location);
            onVerified?.(location, runtime.getSnapshot(), "route");
        });
        return () => { active = false; };
    }, [location, onVerified, routeKind, runtime]);

    const revalidateVisibleSession = useCallback((reason: "resume" | "focus") => {
        if (typeof document === "undefined" || document.visibilityState !== "visible") return;
        const authenticated = runtime.getSnapshot().status === "authenticated";
        if (reason === "focus" && !authenticated) return;
        void runtime.verifySession({ silent: authenticated }).then(() => onVerified?.(location, runtime.getSnapshot(), reason));
    }, [location, onVerified, runtime]);

    useEffect(() => {
        if (routeKind === "public") return;
        const handlePageShow = (event: PageTransitionEvent) => {
            if (event.persisted) {
                setVerifiedLocation(null);
                revalidateVisibleSession("resume");
            }
        };
        const handleFocus = () => { revalidateVisibleSession("focus"); };
        const interval = window.setInterval(handleFocus, 60_000);
        window.addEventListener("pageshow", handlePageShow);
        window.addEventListener("focus", handleFocus);
        document.addEventListener("visibilitychange", handleFocus);
        return () => {
            window.clearInterval(interval);
            window.removeEventListener("pageshow", handlePageShow);
            window.removeEventListener("focus", handleFocus);
            document.removeEventListener("visibilitychange", handleFocus);
        };
    }, [revalidateVisibleSession, routeKind]);

    const routeVerified = routeKind === "public" || verifiedLocation === location;
    return { sessionState, routeVerified };
}
