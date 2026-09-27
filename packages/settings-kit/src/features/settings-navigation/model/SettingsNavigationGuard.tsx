"use client";

import { createContext, useCallback, useContext, useEffect, useId, useMemo, useState, type ReactNode } from "react";
import { AlertDialog } from "frontend-shared/ui/alert-dialog";
import { Button } from "frontend-shared/ui/button";
import { Stack } from "frontend-shared/ui/stack";

export interface SettingsLeaveGuardLabels {
    readonly title: string;
    readonly description: string;
    readonly stay: string;
    readonly leave: string;
}

export interface SettingsNavigationGuardProviderProps {
    readonly children: ReactNode;
    readonly labels: SettingsLeaveGuardLabels;
    /** Inject the application's router. Defaults to a full-page browser navigation. */
    readonly navigate?: (href: string) => void;
}

export interface SettingsNavigationGuard {
    readonly isDirty: boolean;
    readonly requestNavigation: (href: string, afterNavigation?: () => void) => void;
}

interface PendingNavigation {
    readonly href: string;
    readonly afterNavigation?: () => void;
}

interface GuardContextValue extends SettingsNavigationGuard {
    readonly setDirty: (id: string, dirty: boolean | null) => void;
}

const GuardContext = createContext<GuardContextValue | null>(null);

/** Shared by settings forms, multi-step modules, and application navigation. */
export function SettingsNavigationGuardProvider({
    children,
    labels,
    navigate,
}: SettingsNavigationGuardProviderProps) {
    const [dirtySources, setDirtySources] = useState<ReadonlyMap<string, boolean>>(() => new Map());
    const [pendingNavigation, setPendingNavigation] = useState<PendingNavigation | null>(null);
    const isDirty = [...dirtySources.values()].some(Boolean);

    const setDirty = useCallback((id: string, dirty: boolean | null) => {
        setDirtySources((current) => {
            const next = new Map(current);
            if (dirty === null) next.delete(id);
            else next.set(id, dirty);
            return next;
        });
    }, []);

    const requestNavigation = useCallback((href: string, afterNavigation?: () => void) => {
        const destination: PendingNavigation = {
            href,
            ...(afterNavigation ? { afterNavigation } : {}),
        };
        if ([...dirtySources.values()].some(Boolean)) {
            setPendingNavigation(destination);
            return;
        }
        if (navigate) navigate(href);
        else window.location.assign(href);
        afterNavigation?.();
    }, [dirtySources, navigate]);

    const completeNavigation = useCallback(() => {
        if (!pendingNavigation) return;
        if (navigate) navigate(pendingNavigation.href);
        else window.location.assign(pendingNavigation.href);
        pendingNavigation.afterNavigation?.();
        setPendingNavigation(null);
    }, [navigate, pendingNavigation]);

    useEffect(() => {
        if (!isDirty) return;
        const warnBeforeUnload = (event: BeforeUnloadEvent) => {
            event.preventDefault();
            event.returnValue = "";
        };
        window.addEventListener("beforeunload", warnBeforeUnload);
        return () => window.removeEventListener("beforeunload", warnBeforeUnload);
    }, [isDirty]);

    const contextValue = useMemo<GuardContextValue>(
        () => ({ isDirty, requestNavigation, setDirty }),
        [isDirty, requestNavigation, setDirty],
    );

    return (
        <GuardContext.Provider value={contextValue}>
            {children}
            <AlertDialog.Root
                open={pendingNavigation !== null}
                onOpenChange={(open) => { if (!open) setPendingNavigation(null); }}
            >
                <AlertDialog.Popup>
                    <Stack gap="stack">
                        <Stack gap="inline-tight">
                            <AlertDialog.Title>{labels.title}</AlertDialog.Title>
                            <AlertDialog.Description>{labels.description}</AlertDialog.Description>
                        </Stack>
                        <Stack gap="inline" className="justify-end sm:flex-row">
                            <AlertDialog.Close
                                render={<Button tone="neutral" type="button">{labels.stay}</Button>}
                            />
                            <Button tone="danger" type="button" onClick={completeNavigation}>
                                {labels.leave}
                            </Button>
                        </Stack>
                    </Stack>
                </AlertDialog.Popup>
            </AlertDialog.Root>
        </GuardContext.Provider>
    );
}

export function useSettingsNavigationGuard(): SettingsNavigationGuard {
    const context = useContext(GuardContext);
    if (!context) throw new Error("useSettingsNavigationGuard must be used inside SettingsNavigationGuardProvider.");
    return context;
}

/** Registers unsaved form or workflow state with the shared navigation guard. */
export function useSettingsUnsavedChanges(isDirty: boolean): void {
    const context = useContext(GuardContext);
    if (!context) throw new Error("useSettingsUnsavedChanges must be used inside SettingsNavigationGuardProvider.");
    const id = useId();
    const { setDirty } = context;

    useEffect(() => {
        setDirty(id, isDirty);
        return () => setDirty(id, null);
    }, [id, isDirty, setDirty]);
}
