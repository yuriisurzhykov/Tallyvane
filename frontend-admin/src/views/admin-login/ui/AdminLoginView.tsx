"use client";

import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { AdminLoginContent } from "./AdminLoginContent";
import { AdminLoginHeader } from "./AdminLoginHeader";
import { useAdminLoginController, type InitialAdminPendingAuthentication } from "../model/useAdminLoginController";

export function AdminLoginView({ returnTo, initialPending, initialError }: {
    readonly returnTo: string | null;
    readonly initialPending?: InitialAdminPendingAuthentication;
    readonly initialError?: "google";
}) {
    const controller = useAdminLoginController(returnTo, initialPending, initialError);
    return <Stack as="main" gap="section-gap"
                  className="min-h-svh items-center justify-center bg-surface-primary px-stack py-section-gap">
        <Surface variant="elevated" className="w-full max-w-md p-stack">
            <Stack gap="stack">
                <AdminLoginHeader t={ controller.t }/>
                <AdminLoginContent controller={ controller }/>
            </Stack>
        </Surface>
    </Stack>;
}
