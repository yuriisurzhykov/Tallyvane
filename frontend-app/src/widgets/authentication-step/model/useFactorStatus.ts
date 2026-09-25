"use client";

import { useCallback, useEffect, useState } from "react";
import type { AuthStringKey } from "../../../features/authentication/model/strings";
import { authClient } from "../../../features/authentication/api/client";
import { useToast } from "frontend-shared/ui/toast";

export type FactorKind = "TOTP" | "EMAIL_OTP" | "BACKUP_CODE";
export const factorLabels: Record<FactorKind, AuthStringKey> = {
    TOTP: "factorAuthenticator", EMAIL_OTP: "factorEmail", BACKUP_CODE: "factorBackup",
};
type Translate = (key: AuthStringKey, vars?: Record<string, string | number>) => string;

export function useFactorStatus(t: Translate, revision: number) {
    const [enrolled, setEnrolled] = useState<FactorKind[]>([]);
    const { actions } = useToast();

    const refreshStatus = useCallback(async () => {
        const status = await authClient.get<{ enrolled: string[] }>("/mfa/status");
        setEnrolled(status.enrolled.filter((method): method is FactorKind => Object.hasOwn(factorLabels, method)));
    }, []);

    useEffect(() => {
        void refreshStatus().catch(() => { actions.add({ title: t("securityLoadFailed"), tone: "danger" }); });
    }, [actions, revision, refreshStatus, t]);

    return { enrolled, refreshStatus };
}
