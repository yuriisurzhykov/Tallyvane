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

export function useFactorStatus(t: Translate, emailMfaEnrollmentChallengeId: string) {
    const [enrolled, setEnrolled] = useState<FactorKind[]>([]);
    const [recentlyAuthenticated, setRecentlyAuthenticated] = useState(false);
    const [googleReauthenticationAvailable, setGoogleReauthenticationAvailable] = useState(false);
    const { actions } = useToast();

    const refreshStatus = useCallback(async () => {
        const status = await authClient.get<{ enrolled: string[]; recentlyAuthenticated: boolean }>("/mfa/status");
        setEnrolled(status.enrolled.filter((method): method is FactorKind => Object.hasOwn(factorLabels, method)));
        setRecentlyAuthenticated(status.recentlyAuthenticated);
    }, []);

    useEffect(() => {
        const load = async () => {
            try {
                await refreshStatus();
                const providers = await authClient.get<{ google?: boolean }>("/providers");
                if (providers.google !== true) return;
                const link = await authClient.get<{ googleLinked: boolean }>("/google/status");
                setGoogleReauthenticationAvailable(link.googleLinked);
            } catch {
                actions.add({ title: t("securityLoadFailed"), tone: "danger" });
            }
            const result = new URLSearchParams(window.location.search).get("reauth");
            if (result) {
                window.history.replaceState({}, "", window.location.pathname);
                if (result === "success") {
                    actions.add({ title: t("reauthenticated"), tone: "success" });
                    await refreshStatus();
                } else {
                    actions.add({ title: t(result === "cancelled" ? "googleCancelled" : "reauthFailed"), tone: "attention" });
                }
            }
        };
        void load();
    }, [actions, emailMfaEnrollmentChallengeId, refreshStatus, t]);

    return { enrolled, recentlyAuthenticated, setRecentlyAuthenticated, googleReauthenticationAvailable, refreshStatus };
}
