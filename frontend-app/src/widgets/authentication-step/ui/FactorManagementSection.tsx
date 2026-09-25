"use client";

import QRCode from "qrcode";
import { useState, type SubmitEvent } from "react";
import { authClient } from "../../../features/authentication/api/client";
import type { AuthStepProps } from "../model/AuthStepProps";
import { AuthenticationActionForm } from "./AuthenticationActionForm";
import { Button } from "frontend-shared/ui/button";
import { Field } from "frontend-shared/ui/field";
import { Form } from "frontend-shared/ui/form";
import { Image } from "frontend-shared/ui/image";
import { Input } from "frontend-shared/ui/input";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { useToast } from "frontend-shared/ui/toast";
import styles from "./auth-step.module.css";

export function FactorManagementSection({ t, enrolled, onFactorChanged }: {
    t: AuthStepProps["t"]; enrolled: boolean; onFactorChanged: () => Promise<void>;
}) {
    const [totpUri, setTotpUri] = useState("");
    const [qrCode, setQrCode] = useState("");
    const [removeRequested, setRemoveRequested] = useState(false);
    const [confirming, setConfirming] = useState(false);
    const { actions } = useToast();
    const secret = totpUri ? new URL(totpUri).searchParams.get("secret") ?? "" : "";

    async function start(proof: string) {
        const result = await authClient.postWithHeaders<{ otpauthUri: string }>("/mfa/enroll", { kind: "TOTP" },
            { "X-Action-Proof": proof });
        setTotpUri(result.otpauthUri);
        setQrCode(await QRCode.toDataURL(result.otpauthUri, { margin: 1, width: 208 }));
    }

    async function confirm(event: SubmitEvent<HTMLFormElement>) {
        event.preventDefault();
        setConfirming(true);
        try {
            await authClient.post("/mfa/confirm", {
                kind: "TOTP", code: String(new FormData(event.currentTarget).get("totpCode") ?? ""),
            });
            setTotpUri("");
            setQrCode("");
            await onFactorChanged();
            actions.add({ title: t("authenticatorEnabled"), tone: "success" });
        } catch {
            actions.add({ title: t("factorEnrollmentFailed"), tone: "danger" });
        } finally {
            setConfirming(false);
        }
    }

    async function remove(proof: string) {
        await authClient.postWithHeaders("/mfa/disable", { kind: "TOTP", confirmed: true },
            { "X-Action-Proof": proof });
        setRemoveRequested(false);
        await onFactorChanged();
        actions.add({ title: t("factorDisabled", { factor: t("factorAuthenticator") }), tone: "success" });
    }

    return <Stack gap="stack" as="section" aria-labelledby="authenticator-panel-title">
        <Text as="h2" variant="title2" id="authenticator-panel-title">{t("factorAuthenticator")}</Text>
        <Text variant="body" color="muted">{t(enrolled ? "securityAuthenticatorActive" : "enrollDescription")}</Text>
        {enrolled ? <>
            {!removeRequested ? <Button tone="neutral" type="button" onClick={() => { setRemoveRequested(true); }}>
                {t("remove")}
            </Button> : <>
                <Text variant="body">{t("disableFactorDescription")}</Text>
                <AuthenticationActionForm action="MANAGE_SECOND_FACTORS" t={t} tone="danger"
                    submitLabel={t("disableFactorConfirm")} onAuthorized={remove} />
                <Button tone="ghost" type="button" onClick={() => { setRemoveRequested(false); }}>{t("cancel")}</Button>
            </>}
        </> : totpUri ? <>
            <div className={styles.securityProgress} aria-label={t("securityStepTwo")}><span/><span/></div>
            {qrCode ? <Image className={styles.qr ?? ""} src={qrCode} alt={t("authenticatorQr")} /> :
                <Text variant="body" role="status">{t("qrUnavailable")}</Text>}
            <Text variant="small" color="muted">{t("manualSetupKey")}</Text>
            <Text variant="small" as="code" className={styles.secret ?? ""}>{secret}</Text>
            <Button tone="ghost" size="sm" type="button" onClick={() => {
                void navigator.clipboard.writeText(secret).then(() => {
                    actions.add({ title: t("copied"), tone: "success" });
                }).catch(() => { actions.add({ title: t("copyFailed"), tone: "attention" }); });
            }}>{t("copy")}</Button>
            <Form onSubmit={event => { void confirm(event); }}>
                <Field label={t("codeFromAuthenticator")}>
                    <Input name="totpCode" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} required />
                </Field>
                <Button tone="primary" type="submit" loading={confirming}>{t("confirmAuthenticator")}</Button>
            </Form>
        </> : <>
            <div className={styles.securityProgress} aria-label={t("securityStepOne")}><span/></div>
            <Text variant="small" color="muted">{t("securityVerifyFirst")}</Text>
            <AuthenticationActionForm action="MANAGE_SECOND_FACTORS" t={t}
                submitLabel={t("enrollStart")} onAuthorized={start} />
        </>}
    </Stack>;
}
