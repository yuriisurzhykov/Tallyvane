"use client";

import { useRouter } from "next/navigation";
import type { SubmitEvent } from "react";
import type { AuthStringKey } from "../../../features/authentication/model/strings";
import type { AuthStepProps } from "../model/AuthStepProps";
import { useFactorStatus, factorLabels, type FactorKind } from "../model/useFactorStatus";
import { useFactorSecurityActions } from "../model/useFactorSecurityActions";
import { useAuthenticatorEnrollment } from "../model/useAuthenticatorEnrollment";
import { Button } from "frontend-shared/ui/button";
import { Drawer } from "frontend-shared/ui/drawer";
import { Field } from "frontend-shared/ui/field";
import { Form } from "frontend-shared/ui/form";
import { Image } from "frontend-shared/ui/image";
import { Input } from "frontend-shared/ui/input";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import styles from "./auth-step.module.css";

type Translate = (key: AuthStringKey, vars?: Record<string, string | number>) => string;

export function FactorManagementSection({ t, emailMfaEnrollmentChallengeId }: Pick<AuthStepProps, "emailMfaEnrollmentChallengeId" | "t">) {
    const router = useRouter();
    const status = useFactorStatus(t, emailMfaEnrollmentChallengeId);
    const security = useFactorSecurityActions(t, status.refreshStatus);
    const authenticator = useAuthenticatorEnrollment(t, status.refreshStatus);

    return (
        <Stack as="section" gap="stack" className={styles.form ?? ""} aria-labelledby="factor-management-heading">
            <Text variant="title2" as="h2" id="factor-management-heading">{t("factorManagementTitle")}</Text>
            <Text variant="body" color="muted">{t("factorManagementDescription")}</Text>
            <FactorList enrolled={status.enrolled} recentlyAuthenticated={status.recentlyAuthenticated} t={t}
                        onRemove={security.setDisableKind} />
            <ReauthenticationForms password={security.password} setPassword={security.setPassword} busy={security.busy}
                                  googleAvailable={status.googleReauthenticationAvailable} t={t}
                                  onPassword={security.reauthenticate}
                                  onGoogle={() => { router.push("/api/v1/auth/google/reauth/start"); }} />
            { !status.enrolled.includes("TOTP") && <AuthenticatorEnrollment t={t} state={authenticator} /> }
            <FactorRemovalDialog kind={security.disableKind} setKind={security.setDisableKind} busy={security.busy}
                                 t={t} onConfirm={security.disableFactor} />
        </Stack>
    );
}

function FactorList({
    enrolled, recentlyAuthenticated, t, onRemove,
}: { enrolled: FactorKind[]; recentlyAuthenticated: boolean; t: Translate; onRemove: (kind: FactorKind | null) => void }) {
    return (
        <Stack gap="inline-tight">
            <Text variant="small" color="muted" role="status" aria-live="polite">
                {t(recentlyAuthenticated ? "recentReauthenticationReady" : "recentReauthenticationNeeded")}
            </Text>
            {enrolled.length === 0
                ? <Text variant="body" color="muted">{t("noEnrolledFactors")}</Text>
                : <Stack as="ul" gap="inline-tight" aria-label={t("factorManagementTitle")}>
                    {enrolled.map(kind => <Row as="li" key={kind} gap="inline" className={styles.row ?? ""}>
                        <Text variant="body">{t(factorLabels[kind])}</Text>
                        <Button tone="ghost" size="sm" type="button" onClick={() => { onRemove(kind); }}>
                            {t("remove")}
                        </Button>
                    </Row>)}
                </Stack>}
        </Stack>
    );
}

function ReauthenticationForms({
    password, setPassword, busy, googleAvailable, t, onPassword, onGoogle,
}: {
    password: string; setPassword: (password: string) => void; busy: boolean; googleAvailable: boolean;
    t: Translate; onPassword: (event: SubmitEvent<HTMLFormElement>) => Promise<void>; onGoogle: () => void;
}) {
    return (
        <Stack gap="inline">
            <Form onSubmit={event => { void onPassword(event); }}>
                <Field label={t("reauthPasswordLabel")}>
                    <Input type="password" autoComplete="current-password" value={password} required
                           onChange={event => { setPassword(event.target.value); }} />
                </Field>
                <Button tone="primary" type="submit" loading={busy}>{t("reauthenticateWithPassword")}</Button>
            </Form>
            {googleAvailable && <Button tone="neutral" type="button" onClick={onGoogle}>{t("reauthenticateWithGoogle")}</Button>}
        </Stack>
    );
}

function AuthenticatorEnrollment({
    t, state,
}: { t: Translate; state: ReturnType<typeof useAuthenticatorEnrollment> }) {
    const secret = state.totpUri ? new URL(state.totpUri).searchParams.get("secret") ?? state.totpUri : "";
    return (
        <Form onSubmit={event => { void state.submit(event); }}>
            {state.totpUri && <Stack gap="inline-tight">
                {state.qrCode ? <Image className={styles.qr ?? ""} src={state.qrCode} alt={t("authenticatorQr")} />
                    : <Text variant="body" role="status">{t("qrUnavailable")}</Text>}
                <Text variant="small" color="muted">{t("manualSetupKey")}</Text>
                <Text variant="small" as="code" className={styles.secret ?? ""}>{secret}</Text>
                <Button tone="ghost" size="sm" type="button" onClick={() => { void state.copySecret(); }}>{t("copy")}</Button>
                <Field label={t("codeFromAuthenticator")}>
                    <Input value={state.code} inputMode="numeric" autoComplete="one-time-code" maxLength={6} required
                           onChange={event => { state.setCode(event.target.value.replace(/\D/g, "").slice(0, 6)); }} />
                </Field>
            </Stack>}
            <Button tone="neutral" type="submit" loading={state.busy}>
                {t(state.totpUri ? "confirmAuthenticator" : "enrollStart")}
            </Button>
        </Form>
    );
}

function FactorRemovalDialog({
    kind, setKind, busy, t, onConfirm,
}: { kind: FactorKind | null; setKind: (kind: FactorKind | null) => void; busy: boolean; t: Translate; onConfirm: () => Promise<void> }) {
    return (
        <Drawer.Root open={kind !== null} onOpenChange={open => { if (!open) setKind(null); }}>
            <Drawer.Popup>
                <Drawer.Title>{t("disableFactorTitle")}</Drawer.Title>
                <Drawer.Description>{t("disableFactorDescription")}</Drawer.Description>
                <Stack gap="inline">
                    {kind && <Text variant="body">{t(factorLabels[kind])}</Text>}
                    <Button tone="danger" type="button" loading={busy} onClick={() => { void onConfirm(); }}>
                        {t("disableFactorConfirm")}
                    </Button>
                    <Button tone="ghost" type="button" onClick={() => { setKind(null); }}>{t("cancel")}</Button>
                </Stack>
            </Drawer.Popup>
        </Drawer.Root>
    );
}
