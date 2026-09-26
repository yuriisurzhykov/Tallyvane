"use client";

import { useEffect, useState } from "react";
import { AuthError, authClient } from "../../../features/authentication/api/client";
import type { AuthStringKey } from "../../../features/authentication/model/strings";
import { AuthenticationActionForm } from "./AuthenticationActionForm";
import { Button } from "frontend-shared/ui/button";
import { Drawer } from "frontend-shared/ui/drawer";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { useToast } from "frontend-shared/ui/toast";
import styles from "./auth-step.module.css";

type Translate = (key: AuthStringKey, vars?: Record<string, string | number>) => string;

export function GoogleAccountSection({ t, linked, onLinkedChange }: {
    readonly t: Translate; readonly linked: boolean; readonly onLinkedChange: (linked: boolean) => void;
}) {
    const [confirmOpen, setConfirmOpen] = useState(false);
    const { actions } = useToast();

    useEffect(() => {
        const result = new URLSearchParams(window.location.search).get("google");
        if (result === "linked") actions.add({ title: t("googleLinkedToast"), tone: "success" });
        if (result === "cancelled") actions.add({ title: t("googleCancelled"), tone: "attention" });
        if (result && result !== "linked" && result !== "cancelled") {
            actions.add({ title: t("googleLinkFailed"), tone: "danger" });
        }
    }, [actions, t]);

    return <Stack as="section" gap="stack" className={styles.form ?? ""} aria-labelledby="google-account-heading">
        <Text variant="title2" as="h2" id="google-account-heading">{t("googleAccountTitle")}</Text>
        <Text variant="body" color="muted">{t(linked ? "googleConnected" : "googleNotConnected")}</Text>
        {linked ? <>
            <Button tone="neutral" type="button" onClick={() => { setConfirmOpen(true); }}>{t("unlinkGoogle")}</Button>
            <Drawer.Root open={confirmOpen} onOpenChange={setConfirmOpen}>
                <Drawer.Popup>
                    <Drawer.Title>{t("unlinkGoogleTitle")}</Drawer.Title>
                    <Drawer.Description>{t("unlinkGoogleDescription")}</Drawer.Description>
                    <AuthenticationActionForm action="CHANGE_PRIMARY_CREDENTIAL" t={t} tone="danger"
                        submitLabel={t("unlinkGoogleConfirm")}
                        onError={reason => {
                            if (reason instanceof AuthError && reason.status === 409) {
                                actions.add({ title: t("lastSignInMethod"), tone: "danger" });
                            }
                        }}
                        onAuthorized={async proof => {
                            await authClient.postWithHeaders("/google/unlink", {}, { "X-Action-Proof": proof });
                            onLinkedChange(false);
                            setConfirmOpen(false);
                            actions.add({ title: t("googleUnlinkedToast"), tone: "success" });
                        }} />
                    <Button tone="ghost" type="button" onClick={() => { setConfirmOpen(false); }}>{t("cancel")}</Button>
                </Drawer.Popup>
            </Drawer.Root>
        </> : <AuthenticationActionForm action="CHANGE_PRIMARY_CREDENTIAL" t={t} submitLabel={t("linkGoogle")}
            onAuthorized={async proof => {
                const result = await authClient.post<{ url: string }>("/google/link/start", { actionProof: proof });
                window.location.assign(result.url);
            }} />}
    </Stack>;
}
