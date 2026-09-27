"use client";

import { useCallback, useEffect, useState } from "react";
import type { AuthStringKey } from "../../../features/authentication/model/strings";
import { authClient } from "../../../features/authentication/api/client";
import { useToast } from "frontend-shared/ui/toast";

export type FactorKind = "TOTP" | "EMAIL_OTP";
export const factorLabels: Record<FactorKind, AuthStringKey> = {
    TOTP: "factorAuthenticator", EMAIL_OTP: "factorEmail",
};
type Translate = (key: AuthStringKey, vars?: Record<string, string | number>) => string;

export function useFactorStatus(t: Translate, revision: number) {
    const [enrolled, setEnrolled] = useState<FactorKind[]>([]);
    const [recoveryCodesIssued, setRecoveryCodesIssued] = useState(false);
    const { actions } = useToast();

    const refreshStatus = useCallback(async () => {
        const status = await authClient.get<{ enrolled: string[]; recoveryCodesIssued: boolean }>("/mfa/status");
        setEnrolled(status.enrolled.filter((method): method is FactorKind => Object.hasOwn(factorLabels, method)));
        setRecoveryCodesIssued(status.recoveryCodesIssued);
    }, []);

    useEffect(() => {
        queueMicrotask(() => {
            void refreshStatus().catch(() => { actions.add({ title: t("securityLoadFailed"), tone: "danger" }); });
        });
    }, [actions, revision, refreshStatus, t]);

    return { enrolled, recoveryCodesIssued, refreshStatus };
}
