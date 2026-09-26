"use client";

import QRCode from "qrcode";
import { useState } from "react";
import type { SubmitEvent } from "react";
import type { AuthStringKey } from "../../../features/authentication/model/strings";
import { authClient } from "../../../features/authentication/api/client";
import { useToast } from "frontend-shared/ui/toast";

type Translate = (key: AuthStringKey, vars?: Record<string, string | number>) => string;

export function useAuthenticatorEnrollment(
    t: Translate,
    refreshStatus: () => Promise<void>,
    getActionProof: () => string,
    clearActionProof: () => void,
    onFactorChanged: () => void,
) {
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
                const proof = getActionProof();
                if (!proof) throw new Error("Action proof required");
                clearActionProof();
                const result = await authClient.postWithHeaders<{ otpauthUri: string }>("/mfa/enroll", { kind: "TOTP" }, {
                    "X-Action-Proof": proof,
                });
                setTotpUri(result.otpauthUri);
                setQrCode(await QRCode.toDataURL(result.otpauthUri, { margin: 1, width: 208 }));
                return;
            }
            const proof = getActionProof();
            if (!proof) throw new Error("Action proof required");
            clearActionProof();
            await authClient.postWithHeaders("/mfa/confirm", { kind: "TOTP", code }, { "X-Action-Proof": proof });
            setTotpUri("");
            setQrCode("");
            setCode("");
            await refreshStatus();
            onFactorChanged();
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
