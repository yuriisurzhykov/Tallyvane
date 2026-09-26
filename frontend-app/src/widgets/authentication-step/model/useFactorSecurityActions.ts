"use client";

import { useState } from "react";
import type { AuthStringKey } from "../../../features/authentication/model/strings";
import { AuthError, authClient } from "../../../features/authentication/api/client";
import { useToast } from "frontend-shared/ui/toast";
import { factorLabels, type FactorKind } from "./useFactorStatus";

type Translate = (key: AuthStringKey, vars?: Record<string, string | number>) => string;

export function useFactorSecurityActions(t: Translate, refreshStatus: () => Promise<void>, onFactorChanged: () => void) {
    const [actionProof, setActionProof] = useState("");
    const [disableKind, setDisableKind] = useState<FactorKind | null>(null);
    const [busy, setBusy] = useState(false);
    const { actions } = useToast();

    function acceptActionProof(proof: string) {
        setActionProof(proof);
        actions.add({ title: t("reauthenticated"), tone: "success" });
    }

    async function disableFactor() {
        if (!disableKind || !actionProof) return;
        setBusy(true);
        const kind = disableKind;
        const proof = actionProof;
        setActionProof("");
        try {
            await authClient.postWithHeaders("/mfa/disable", { kind, confirmed: true }, {
                "X-Action-Proof": proof,
            });
            setDisableKind(null);
            await refreshStatus();
            onFactorChanged();
            actions.add({ title: t("factorDisabled", { factor: t(factorLabels[kind]) }), tone: "success" });
        } catch (reason) {
            const key = reason instanceof AuthError && reason.status === 409 ? "factorRequiredByPolicy" :
                reason instanceof AuthError && reason.status === 401 ? "reauthenticationRequired" : "factorDisableFailed";
            actions.add({ title: t(key), tone: "danger" });
        } finally {
            setBusy(false);
        }
    }

    return { actionProof, acceptActionProof, clearActionProof: () => setActionProof(""),
        disableKind, setDisableKind, busy, disableFactor };
}
