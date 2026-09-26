"use client";

import { useState, type ReactNode, type SubmitEvent } from "react";
import type { AccountAction, ProofTokenKind } from "../../../features/authentication/api/actionProof";
import type { AuthStringKey } from "../../../features/authentication/model/strings";
import { useAuthenticationActionProof } from "../model/useAuthenticationActionProof";
import { Button } from "frontend-shared/ui/button";
import { Field } from "frontend-shared/ui/field";
import { Form } from "frontend-shared/ui/form";
import { Input } from "frontend-shared/ui/input";
import { PasswordField } from "frontend-shared/ui/password-field";
import { Select } from "frontend-shared/ui/select";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";

type Translate = (key: AuthStringKey, vars?: Record<string, string | number>) => string;
type ActionProofState = ReturnType<typeof useAuthenticationActionProof>;

const tokenLabels: Record<ProofTokenKind, AuthStringKey> = {
    PASSWORD: "currentPassword",
    GOOGLE: "reauthenticateWithGoogle",
    EMAIL_SIGN_IN_CODE: "emailCodeLabel",
    TOTP: "factorAuthenticator",
    EMAIL_FACTOR_CODE: "factorEmail",
    BACKUP_CODE: "factorBackup",
};

const proofErrorLabels: Record<NonNullable<ActionProofState["error"]>, AuthStringKey> = {
    load: "actionProofLoadFailed",
    required: "actionProofRequired",
    invalid: "reauthFailed",
    google: "actionProofGoogleFailed",
};

function isEmailCode(kind: ProofTokenKind): kind is "EMAIL_SIGN_IN_CODE" | "EMAIL_FACTOR_CODE" {
    return kind === "EMAIL_SIGN_IN_CODE" || kind === "EMAIL_FACTOR_CODE";
}

function ActionProofSchemeField({ proof, busy, t }: {
    proof: Pick<ActionProofState, "schemes" | "scheme" | "selectScheme">;
    busy: boolean;
    t: Translate;
}) {
    return <Select.Root value={proof.scheme?.id ?? ""} onValueChange={id => { proof.selectScheme(id ?? ""); }}>
        <Select.Label>{t("actionProofSchemeLabel")}</Select.Label>
        <Select.Trigger disabled={busy}>
            <Select.Value />
            <Select.Icon />
        </Select.Trigger>
        <Select.Popup>
            {proof.schemes.map(scheme => <Select.Item key={scheme.id} value={scheme.id}>
                {scheme.requiredTokens.map(kind => t(tokenLabels[kind])).join(" + ")}
                {` · ${t("actionProofLevel", { rank: scheme.assuranceRank })}`}
            </Select.Item>)}
        </Select.Popup>
    </Select.Root>;
}

function ActionProofToken({ kind, proof, busy, t }: {
    kind: ProofTokenKind;
    proof: Pick<ActionProofState, "values" | "challenges" | "googleReady" | "setValue" | "verifyWithGoogle">;
    busy: boolean;
    t: Translate;
}) {
    const label = t(tokenLabels[kind]);

    if (kind === "GOOGLE") {
        return <Stack gap="inline-tight">
            <Text variant="small" color="primary">{label}</Text>
            <Button type="button" tone="neutral" disabled={busy} onClick={() => { void proof.verifyWithGoogle(); }}>
                {t("actionProofGoogleButton")}
            </Button>
            {proof.googleReady && <Text variant="small" color="muted" role="status">{t("actionProofGoogleReady")}</Text>}
        </Stack>;
    }

    if (isEmailCode(kind) && !proof.challenges[kind]) {
        return <Stack gap="inline-tight">
            <Text variant="small" color="primary">{label}</Text>
            <Text variant="small" color="muted">{t("actionProofEmailRequest")}</Text>
        </Stack>;
    }

    const control = kind === "PASSWORD"
        ? <PasswordField showPasswordLabel={t("showPassword")} hidePasswordLabel={t("hidePassword")}
            autoComplete="current-password" value={proof.values[kind] ?? ""} disabled={busy}
            onChange={event => { proof.setValue(kind, event.target.value); }} />
        : <Input type="text" autoComplete="one-time-code" value={proof.values[kind] ?? ""} disabled={busy}
            onChange={event => { proof.setValue(kind, event.target.value); }} />;

    return <Field label={label} required>{control}</Field>;
}

function ActionProofError({ error, t }: { error: ActionProofState["error"]; t: Translate }) {
    if (!error) return null;
    return <Text variant="small" tone="danger" role="alert">{t(proofErrorLabels[error])}</Text>;
}

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
    const needsEmailCode = proof.scheme?.requiredTokens.some(kind => isEmailCode(kind) && !proof.challenges[kind]) ?? false;

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
        {proof.schemes.length > 1 && <ActionProofSchemeField proof={proof} busy={busy} t={t} />}
        {proof.scheme && <Stack gap="inline-tight">
            {proof.scheme.requiredTokens.map(kind => <ActionProofToken key={kind} kind={kind} proof={proof} busy={busy} t={t} />)}
        </Stack>}
        {children}
        <ActionProofError error={proof.error} t={t} />
        {operationError && <Text variant="small" tone="danger" role="alert">{t("requestFailed")}</Text>}
        <Button tone={tone} type={needsEmailCode ? "button" : "submit"} loading={busy}
            disabled={proof.loading || !proof.scheme}
            onClick={needsEmailCode ? () => { void proof.sendEmailCodes(); } : undefined}>
            {needsEmailCode ? t("actionProofSendEmailCodes") : submitLabel}
        </Button>
    </Form>;
}
