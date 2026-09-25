"use client";

import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { AdminEnrollmentPanel } from "./AdminEnrollmentPanel";
import { AdminLoginContent } from "./AdminLoginContent";
import { AdminLoginHeader } from "./AdminLoginHeader";
import { useAdminLoginController } from "../model/useAdminLoginController";

export function AdminLoginView({ returnTo }: { readonly returnTo: string | null }) {
    const controller = useAdminLoginController(returnTo);
    return <Stack as="main" gap="section-gap"
                  className="min-h-svh items-center justify-center bg-surface-primary px-stack py-section-gap">
        <Surface variant="elevated" className="w-full max-w-md p-stack">
            <Stack gap="stack">
                <AdminLoginHeader t={ controller.t }/>
                <AdminLoginContent controller={ controller }/>
                { controller.state.screen === "checking" && <AdminEnrollmentPanel controller={ controller }/> }
            </Stack>
        </Surface>
    </Stack>;
}
