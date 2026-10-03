"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import { Button } from "frontend-shared/ui/button";
import { Callout } from "frontend-shared/ui/callout";
import { Dialog } from "frontend-shared/ui/dialog";
import { Link } from "frontend-shared/ui/link";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { useStrings } from "@/shared/i18n";
import type { GoogleStepUp } from "@/entities/viewer";
import type { StepUpSession } from "../model/StepUpSession";

export interface StepUpDialogProps {
    readonly session: StepUpSession;
    readonly stepUp: GoogleStepUp;
}

type Address = { readonly state: "preparing" } | { readonly state: "failed" } | { readonly state: "ready"; readonly url: string };

const POPUP = "popup,width=480,height=680";

/**
 * The prompt that interrupts the page when a dangerous act needs a recent proof (ADR-092). Open exactly
 * while the act waits for it. Unlike the prompt for an ended session it can be dismissed: the person
 * signed in a moment ago, so there is nothing to lose by saying no, and the act then fails as it would
 * have without the prompt.
 */
export function StepUpDialog({ session, stepUp }: StepUpDialogProps) {
    const open = useSyncExternalStore(session.subscribe, session.isWaiting, session.isWaiting);
    return (
        <Dialog.Root
            open={open}
            onOpenChange={(next) => {
                if (!next) session.cancel();
            }}
        >
            <Dialog.Popup>
                <StepUpPrompt session={session} stepUp={stepUp} />
            </Dialog.Popup>
        </Dialog.Root>
    );
}

/**
 * Google opens in a small window so the page underneath stays as it is. The address is fetched as the
 * prompt appears, so the click can open the window synchronously (the one way a pop-up blocker allows it),
 * and the same address is offered as a plain link if it is blocked anyway. Mounted only while the dialog
 * is open, so every appearance starts fresh.
 */
function StepUpPrompt({ session, stepUp }: StepUpDialogProps) {
    const t = useStrings("stepUp");
    const auth = useStrings("auth");
    const [address, setAddress] = useState<Address>({ state: "preparing" });
    const [blocked, setBlocked] = useState(false);

    useEffect(() => {
        stepUp.begin().then(
            (url) => { setAddress({ state: "ready", url }); },
            () => { setAddress({ state: "failed" }); },
        );
    }, [stepUp]);

    const openWindow = (url: string) => {
        if (window.open(url, "tallyvane-step-up", POPUP) === null) {
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
                    <Link href={address.url} target="_blank" rel="noopener">
                        {t("openLink")}
                    </Link>
                </Callout>
            ) : null}
            <Row gap="inline" className="justify-end">
                <Button tone="ghost" size="sm" onClick={() => { session.cancel(); }}>
                    {t("cancel")}
                </Button>
            </Row>
        </>
    );
}
