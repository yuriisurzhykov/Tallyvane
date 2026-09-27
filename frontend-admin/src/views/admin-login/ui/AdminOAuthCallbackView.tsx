"use client";

import { useEffect } from "react";
import { resolveAdminLoginReturnTo } from "@/features/admin-login";
import { useAdminLoginStrings } from "@/features/admin-login";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";

export function AdminOAuthCallbackView() {
    const t = useAdminLoginStrings("adminLogin");
    useEffect(() => {
        if (new URLSearchParams(window.location.search).has("error")) {
            window.location.replace("/login?oauthError=1");
            return;
        }
        const saved = sessionStorage.getItem("tallyvane.admin.auth.returnTo");
        sessionStorage.removeItem("tallyvane.admin.auth.returnTo");
        window.location.replace(resolveAdminLoginReturnTo(saved));
    }, []);

    return <Stack as="main" gap="stack" className="min-h-svh items-center justify-center">
        <Text variant="body" role="status">{t("completingSignIn")}</Text>
    </Stack>;
}
