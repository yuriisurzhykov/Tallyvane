"use client";

import { useEffect, useState, type SyntheticEvent } from "react";
import { authClient } from "../../../features/authentication/api/client";
import type { AuthStringKey } from "../../../features/authentication/model/strings";
import { Button } from "frontend-shared/ui/button";
import { Drawer } from "frontend-shared/ui/drawer";
import { Field } from "frontend-shared/ui/field";
import { Form } from "frontend-shared/ui/form";
import { Input } from "frontend-shared/ui/input";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { useToast } from "frontend-shared/ui/toast";
import styles from "./auth-step.module.css";

type Translate = (key: AuthStringKey, vars?: Record<string, string | number>) => string;

export function GoogleAccountSection({ t }: { readonly t: Translate }) {
    const [available, setAvailable] = useState(false);
    const [linked, setLinked] = useState(false);
    const [busy, setBusy] = useState(false);
    const [password, setPassword] = useState("");
    const [confirmOpen, setConfirmOpen] = useState(false);
    const { actions } = useToast();

    useEffect(() => {
        void loadProviderStatus(setAvailable, setLinked);
    }, []);

    useEffect(() => {
        const result = new URLSearchParams(window.location.search).get("google");
        if (result === "linked") actions.add({ title: t("googleLinkedToast"), tone: "success" });
        if (result === "cancelled") actions.add({ title: t("googleCancelled"), tone: "attention" });
        if (result && result !== "linked" && result !== "cancelled") {
            actions.add({ title: t("googleLinkFailed"), tone: "danger" });
        }
    }, [actions, t]);

    async function beginLink(event: SyntheticEvent<HTMLFormElement>) {
        event.preventDefault();
        setBusy(true);
        try {
            const result = await authClient.post<{ url: string }>("/google/link/start", { password });
            window.location.assign(result.url);
        } catch {
            actions.add({ title: t("googleLinkFailed"), description: t("reauthFailed"), tone: "danger" });
            setBusy(false);
        }
    }

    async function unlink() {
        setBusy(true);
        try {
            await authClient.post<unknown>("/google/unlink", { password });
            setLinked(false);
            setPassword("");
            setConfirmOpen(false);
            actions.add({ title: t("googleUnlinkedToast"), tone: "success" });
        } catch (reason) {
            const key = reason && typeof reason === "object" && "status" in reason && reason.status === 409
                ? "lastSignInMethod"
                : "googleLinkFailed";
            actions.add({ title: t(key), tone: "danger" });
        } finally {
            setBusy(false);
        }
    }

    if (!available) return null;

    return (
        <Stack as="section" gap="stack" className={styles.form ?? ""} aria-labelledby="google-account-heading">
            <Text variant="title2" as="h2" id="google-account-heading">{t("googleAccountTitle")}</Text>
            <Text variant="body" color="muted">{t(linked ? "googleConnected" : "googleNotConnected")}</Text>
            {linked ? (
                <LinkedGoogleAccount t={t} busy={busy} password={password} confirmOpen={confirmOpen}
                    onPasswordChange={setPassword} onConfirmOpenChange={setConfirmOpen} onUnlink={unlink} />
            ) : (
                <GoogleLinkForm t={t} busy={busy} password={password}
                    onPasswordChange={setPassword} onSubmit={beginLink} />
            )}
        </Stack>
    );
}

function LinkedGoogleAccount({
    t, busy, password, confirmOpen, onPasswordChange, onConfirmOpenChange, onUnlink,
}: {
    t: Translate;
    busy: boolean;
    password: string;
    confirmOpen: boolean;
    onPasswordChange: (value: string) => void;
    onConfirmOpenChange: (open: boolean) => void;
    onUnlink: () => Promise<void>;
}) {
    return (
        <>
            <Button tone="neutral" type="button" onClick={() => { onConfirmOpenChange(true); }}>{t("unlinkGoogle")}</Button>
            <Drawer.Root open={confirmOpen} onOpenChange={onConfirmOpenChange}>
                <Drawer.Popup>
                    <Drawer.Title>{t("unlinkGoogleTitle")}</Drawer.Title>
                    <Drawer.Description>{t("unlinkGoogleDescription")}</Drawer.Description>
                    <Form onSubmit={event => { event.preventDefault(); void onUnlink(); }}>
                        <PasswordField t={t} password={password} onChange={onPasswordChange} />
                        <Button tone="danger" type="submit" loading={busy}>{t("unlinkGoogleConfirm")}</Button>
                        <Button tone="ghost" type="button" onClick={() => { onConfirmOpenChange(false); }}>
                            {t("cancel")}
                        </Button>
                    </Form>
                </Drawer.Popup>
            </Drawer.Root>
        </>
    );
}

function GoogleLinkForm({
    t, busy, password, onPasswordChange, onSubmit,
}: {
    t: Translate;
    busy: boolean;
    password: string;
    onPasswordChange: (value: string) => void;
    onSubmit: (event: SyntheticEvent<HTMLFormElement>) => Promise<void>;
}) {
    return (
        <Form onSubmit={event => { void onSubmit(event); }}>
            <PasswordField t={t} password={password} onChange={onPasswordChange} />
            <Button tone="primary" type="submit" loading={busy}>{t("linkGoogle")}</Button>
        </Form>
    );
}

function PasswordField({
    t, password, onChange,
}: { t: Translate; password: string; onChange: (value: string) => void }) {
    return (
        <Field label={t("currentPassword")} required>
            <Input type="password" autoComplete="current-password" value={password} required
                onChange={event => { onChange(event.target.value); }} />
        </Field>
    );
}

async function loadProviderStatus(setAvailable: (available: boolean) => void, setLinked: (linked: boolean) => void) {
    try {
        const providers = await authClient.get<{ google?: boolean }>("/providers");
        if (!providers.google) return;
        const status = await authClient.get<{ googleLinked: boolean }>("/google/status");
        setLinked(status.googleLinked);
        setAvailable(true);
    } catch {
        setAvailable(false);
    }
}
