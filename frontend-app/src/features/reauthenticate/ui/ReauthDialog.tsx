"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import { Button } from "frontend-shared/ui/button";
import { Callout } from "frontend-shared/ui/callout";
import { Dialog } from "frontend-shared/ui/dialog";
import { Link } from "frontend-shared/ui/link";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "@/shared/i18n";
import type { GoogleSignIn } from "@/entities/viewer";
import type { PopupHandoff } from "../model/PopupHandoff";
import type { ReauthSession } from "../model/ReauthSession";

export interface ReauthDialogProps {
    readonly session: ReauthSession;
    readonly signIn: GoogleSignIn;
    readonly handoff: PopupHandoff;
}

type Address = { readonly state: "preparing" } | { readonly state: "failed" } | { readonly state: "ready"; readonly url: string };

const POPUP = "popup,width=480,height=680";

/**
 * The prompt that interrupts the page when the session ends (ADR-084). Open exactly while the session
 * waits for a sign-in; it has no way to be dismissed, because the requests behind it are waiting too.
 */
export function ReauthDialog({ session, signIn, handoff }: ReauthDialogProps) {
    const open = useSyncExternalStore(session.subscribe, session.isWaiting, session.isWaiting);
    return (
        <Dialog.Root open={open}>
            <Dialog.Popup>
                <ReauthPrompt signIn={signIn} handoff={handoff} />
            </Dialog.Popup>
        </Dialog.Root>
    );
}

interface ReauthPromptProps {
    readonly signIn: GoogleSignIn;
    readonly handoff: PopupHandoff;
}

/**
 * Google opens in a small window so the page underneath, and whatever the person was typing, stays as it
 * is. The address is fetched as the prompt appears, so the click can open the window synchronously (the
 * one way a pop-up blocker allows it), and the same address can be offered as a plain link if it is
 * blocked anyway. Mounted only while the dialog is open, so every appearance starts fresh.
 */
function ReauthPrompt({ signIn, handoff }: ReauthPromptProps) {
    const t = useStrings("reauth");
    const auth = useStrings("auth");
    const [address, setAddress] = useState<Address>({ state: "preparing" });
    const [blocked, setBlocked] = useState(false);

    useEffect(() => {
        signIn.begin().then(
            (url) => { setAddress({ state: "ready", url }); },
            () => { setAddress({ state: "failed" }); },
        );
    }, [signIn]);

    const openWindow = (url: string) => {
        handoff.expect();
        if (window.open(url, "tallyvane-reauth", POPUP) === null) {
            handoff.forget();
            setBlocked(true);
        }
    };

    return (
        <>
            <Stack gap="stack-tight">
                <Dialog.Title>{t("title")}</Dialog.Title>
                <Dialog.Description>{t("lead")}</Dialog.Description>
            </Stack>
            <Button
                tone="neutral"
                size="lg"
                loading={address.state === "preparing"}
                disabled={address.state !== "ready"}
                onClick={() => { if (address.state === "ready") openWindow(address.url); }}
                className="w-full"
            >
                {auth("continueWithGoogle")}
            </Button>
            {address.state === "failed" ? <Callout tone="danger">{t("failed")}</Callout> : null}
            {blocked && address.state === "ready" ? (
                <Callout tone="info">
                    {t("blocked")}{" "}
                    <Link href={address.url} target="_blank" rel="noopener" onClick={() => { handoff.expect(); }}>
                        {t("openLink")}
                    </Link>
                </Callout>
            ) : null}
            <Text variant="caption" color="muted">{t("different")}</Text>
        </>
    );
}
