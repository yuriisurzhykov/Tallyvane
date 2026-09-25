"use client";

import { useState } from "react";
import type { SubmitEvent } from "react";
import type { AuthStringKey } from "../../../features/authentication/model/strings";
import { AuthError, authClient } from "../../../features/authentication/api/client";
import { useToast } from "frontend-shared/ui/toast";
import { factorLabels, type FactorKind } from "./useFactorStatus";

type Translate = (key: AuthStringKey, vars?: Record<string, string | number>) => string;

export function useFactorSecurityActions(t: Translate, refreshStatus: () => Promise<void>) {
    const [password, setPassword] = useState("");
    const [disableKind, setDisableKind] = useState<FactorKind | null>(null);
    const [busy, setBusy] = useState(false);
    const { actions } = useToast();

    async function reauthenticate(event: SubmitEvent<HTMLFormElement>) {
        event.preventDefault();
        setBusy(true);
        try {
            await authClient.post("/account/reauth/password", { password });
            setPassword("");
            await refreshStatus();
            actions.add({ title: t("reauthenticated"), tone: "success" });
        } catch {
            actions.add({ title: t("reauthFailed"), description: t("reauthTryAgain"), tone: "danger" });
        } finally {
            setBusy(false);
        }
    }

    async function disableFactor() {
        if (!disableKind) return;
        setBusy(true);
        try {
            await authClient.post("/mfa/disable", { kind: disableKind, confirmed: true });
            setDisableKind(null);
            await refreshStatus();
            actions.add({ title: t("factorDisabled", { factor: t(factorLabels[disableKind]) }), tone: "success" });
        } catch (reason) {
            const key = reason instanceof AuthError && reason.status === 409 ? "factorRequiredByPolicy" :
                reason instanceof AuthError && reason.status === 401 ? "reauthenticationRequired" : "factorDisableFailed";
            actions.add({ title: t(key), tone: "danger" });
            if (key === "reauthenticationRequired") await refreshStatus();
        } finally {
            setBusy(false);
        }
    }

    return { password, setPassword, disableKind, setDisableKind, busy, reauthenticate, disableFactor };
}
