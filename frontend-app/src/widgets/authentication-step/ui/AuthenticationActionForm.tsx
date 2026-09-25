"use client";

import { useState, type ReactNode, type SubmitEvent } from "react";
import type { AccountAction, ProofTokenKind } from "../../../features/authentication/api/actionProof";
import type { AuthStringKey } from "../../../features/authentication/model/strings";
import { useAuthenticationActionProof } from "../model/useAuthenticationActionProof";
import { Button } from "frontend-shared/ui/button";
import { Field } from "frontend-shared/ui/field";
import { Form } from "frontend-shared/ui/form";
import { Input } from "frontend-shared/ui/input";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";

type Translate = (key: AuthStringKey, vars?: Record<string, string | number>) => string;

const tokenLabels: Record<ProofTokenKind, AuthStringKey> = {
    PASSWORD: "currentPassword",
    GOOGLE: "reauthenticateWithGoogle",
    EMAIL_SIGN_IN_CODE: "emailCodeLabel",
    TOTP: "factorAuthenticator",
    EMAIL_FACTOR_CODE: "factorEmail",
    BACKUP_CODE: "factorBackup",
};

export function AuthenticationActionForm({ action, t, submitLabel, onAuthorized, onError, children, tone = "primary" }: {
    action: AccountAction;
    t: Translate;
    submitLabel: string;
    onAuthorized: (proof: string, form: FormData) => Promise<void> | void;
    onError?: (reason: unknown) => void;
    children?: ReactNode;
    tone?: "primary" | "neutral" | "danger";
}) {
    const proof = useAuthenticationActionProof(action);
    const [submitting, setSubmitting] = useState(false);
    const [operationError, setOperationError] = useState(false);
    const busy = proof.busy || submitting;
    const needsEmailCode = proof.scheme?.requiredTokens.some(kind =>
        (kind === "EMAIL_SIGN_IN_CODE" || kind === "EMAIL_FACTOR_CODE") && !proof.challenges[kind],
    ) ?? false;

    async function submit(event: SubmitEvent<HTMLFormElement>) {
        event.preventDefault();
        if (busy) return;
        const target = event.currentTarget;
        const form = new FormData(target);
        setSubmitting(true);
        setOperationError(false);
        try {
            const granted = await proof.authorize();
            if (!granted) return;
            await onAuthorized(granted, form);
            target.reset();
            await proof.refresh();
        } catch (reason) {
            setOperationError(true);
            onError?.(reason);
            void proof.refresh();
        } finally {
            setSubmitting(false);
        }
    }

    return <Form onSubmit={event => { void submit(event); }}>
        {proof.loading && <Text variant="small" color="muted" role="status">{t("preparingVerification")}</Text>}
        {!proof.loading && proof.schemes.length === 0 && !proof.error &&
            <Text variant="small" color="muted" role="status">{t("actionProofNoSchemes")}</Text>}
        {proof.schemes.length > 1 && <Field label={t("actionProofSchemeLabel")}>
            <select value={proof.scheme?.id ?? ""} disabled={busy} onChange={event => { proof.selectScheme(event.target.value); }}>
                {proof.schemes.map(scheme => <option key={scheme.id} value={scheme.id}>
                    {scheme.requiredTokens.map(kind => t(tokenLabels[kind])).join(" + ")}
                    {` · ${t("actionProofLevel", { rank: scheme.assuranceRank })}`}
                </option>)}
            </select>
        </Field>}
        {proof.scheme && <Stack gap="inline-tight">
            {proof.scheme.requiredTokens.map(kind => kind === "GOOGLE" ?
                <Field key={kind} label={t(tokenLabels[kind])}>
                    <Stack gap="inline-tight">
                        <Button type="button" tone="neutral" disabled={busy} onClick={() => { void proof.verifyWithGoogle(); }}>
                            {t("actionProofGoogleButton")}
                        </Button>
                        {proof.googleReady && <Text variant="small" color="muted" role="status">{t("actionProofGoogleReady")}</Text>}
                    </Stack>
                </Field>
                : <Field key={kind} label={t(tokenLabels[kind])}>
                    <Stack gap="inline-tight">
                        {(kind === "EMAIL_SIGN_IN_CODE" || kind === "EMAIL_FACTOR_CODE") && !proof.challenges[kind] &&
                            <Text variant="small" color="muted">{t("actionProofEmailRequest")}</Text>}
                        {(kind !== "EMAIL_SIGN_IN_CODE" && kind !== "EMAIL_FACTOR_CODE" || proof.challenges[kind]) &&
                            <Input type={kind === "PASSWORD" ? "password" : "text"}
                                autoComplete={kind === "PASSWORD" ? "current-password" : "one-time-code"}
                                value={proof.values[kind] ?? ""} disabled={busy} required
                                onChange={event => { proof.setValue(kind, event.target.value); }} />}
                    </Stack>
                </Field>)}
        </Stack>}
        {children}
        {proof.error && <Text variant="small" role="alert">
            {t(proof.error === "load" ? "actionProofLoadFailed" : proof.error === "required" ? "actionProofRequired" :
                proof.error === "google" ? "actionProofGoogleFailed" : "reauthFailed")}
        </Text>}
        {operationError && <Text variant="small" role="alert">{t("requestFailed")}</Text>}
        <Button tone={tone} type={needsEmailCode ? "button" : "submit"} loading={busy}
            disabled={proof.loading || !proof.scheme}
            onClick={needsEmailCode ? () => { void proof.sendEmailCodes(); } : undefined}>
            {needsEmailCode ? t("actionProofSendEmailCodes") : submitLabel}
        </Button>
    </Form>;
}
