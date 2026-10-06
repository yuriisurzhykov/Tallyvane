"use client";

import { useEffect, useRef, useState } from "react";
import { ProblemError, useApi } from "frontend-shared/api";
import { Callout } from "frontend-shared/ui/callout";
import { Spinner } from "frontend-shared/ui/spinner";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "@/shared/i18n";
import { ConfirmationSignal } from "../model/ConfirmationSignal";

type Outcome = "working" | "done" | "failed" | "other-account";

export interface ConfirmStepUpProps {
    /** Leave the window open once done, because it has something more to tell the person. */
    readonly keepOpen?: boolean;
}

/**
 * Google has sent the person back from confirming a dangerous act (and a code, if they use an authenticator,
 * has been given): hand the confirmation to the session, tell the page that waits, and close this window.
 * Sent exactly once even when React runs effects twice.
 */
export function ConfirmStepUp({ keepOpen = false }: ConfirmStepUpProps) {
    const t = useStrings("stepUpContinue");
    const api = useApi();
    const sent = useRef(false);
    const [outcome, setOutcome] = useState<Outcome>("working");

    useEffect(() => {
        if (sent.current) {
            return;
        }
        sent.current = true;
        api.trigger("/step-ups").then(
            () => {
                new ConfirmationSignal().announce();
                setOutcome("done");
                if (!keepOpen) {
                    window.close();
                }
            },
            (failure: unknown) => {
                setOutcome(failure instanceof ProblemError && failure.hasStatus(403) ? "other-account" : "failed");
            },
        );
    }, [api, keepOpen]);

    switch (outcome) {
        case "working":
            return <Spinner size="lg" label={t("working")} />;
        case "done":
            return <Text variant="body">{t("done")}</Text>;
        case "other-account":
            return <Callout tone="danger">{t("otherAccount")}</Callout>;
        case "failed":
            return (
                <Stack gap="stack">
                    <Callout tone="danger">{t("failed")}</Callout>
                </Stack>
            );
    }
}
