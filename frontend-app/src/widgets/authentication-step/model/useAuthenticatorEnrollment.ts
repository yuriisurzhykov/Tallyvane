"use client";

import QRCode from "qrcode";
import { useState } from "react";
import type { SubmitEvent } from "react";
import type { AuthStringKey } from "../../../features/authentication/model/strings";
import { authClient } from "../../../features/authentication/api/client";
import { useToast } from "frontend-shared/ui/toast";

type Translate = (key: AuthStringKey, vars?: Record<string, string | number>) => string;

export function useAuthenticatorEnrollment(t: Translate, refreshStatus: () => Promise<void>) {
    const [totpUri, setTotpUri] = useState("");
    const [qrCode, setQrCode] = useState("");
    const [code, setCode] = useState("");
    const [busy, setBusy] = useState(false);
    const { actions } = useToast();

    async function submit(event: SubmitEvent<HTMLFormElement>) {
        event.preventDefault();
        setBusy(true);
        try {
            if (!totpUri) {
                const result = await authClient.post<{ otpauthUri: string }>("/mfa/enroll", { kind: "TOTP" });
                setTotpUri(result.otpauthUri);
                setQrCode(await QRCode.toDataURL(result.otpauthUri, { margin: 1, width: 208 }));
                return;
            }
            await authClient.post("/mfa/confirm", { kind: "TOTP", code });
            setTotpUri("");
            setQrCode("");
            setCode("");
            await refreshStatus();
            actions.add({ title: t("authenticatorEnabled"), tone: "success" });
        } catch {
            actions.add({ title: t("factorEnrollmentFailed"), tone: "danger" });
        } finally {
            setBusy(false);
        }
    }

    async function copySecret() {
        const secret = totpUri ? new URL(totpUri).searchParams.get("secret") ?? "" : "";
        try {
            await navigator.clipboard.writeText(secret);
            actions.add({ title: t("copied"), tone: "success" });
        } catch {
            actions.add({ title: t("copyFailed"), tone: "attention" });
        }
    }

    return { totpUri, qrCode, code, setCode, busy, submit, copySecret };
}
