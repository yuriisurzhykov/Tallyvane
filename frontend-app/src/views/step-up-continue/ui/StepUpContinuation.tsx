"use client";

import { useState } from "react";
import { Callout } from "frontend-shared/ui/callout";
import { Spinner } from "frontend-shared/ui/spinner";
import { Stack } from "frontend-shared/ui/stack";
import { useSignInState } from "@/entities/sign-in";
import { SecondFactorForm } from "@/features/answer-second-factor";
import { ConfirmStepUp } from "@/features/reauthenticate";
import { useStrings } from "@/shared/i18n";

/**
 * Google has sent the person back from confirming a dangerous act. It asks where the confirmation stands:
 * a code still wanted is asked for here, in this window, with the same field as a sign-in; once everything is
 * proved the confirmation is taken. A recovery code retires the authenticator (ADR-093), so after one the
 * window stays open to say so instead of closing under the person's eyes.
 */
export function StepUpContinuation() {
    const t = useStrings("stepUpContinue");
    const { status, refresh } = useSignInState();
    const [over, setOver] = useState(false);
    const [usedRecovery, setUsedRecovery] = useState(false);

    if (over) {
        return <Callout tone="danger">{t("ended")}</Callout>;
    }

    switch (status.status) {
        case "loading":
            return <Spinner size="lg" label={t("working")} />;
        case "failed":
            return <Callout tone="danger">{t("failed")}</Callout>;
        case "none":
            return <Callout tone="danger">{t("ended")}</Callout>;
        case "ready":
            return status.state.proceed({
                answer: (offer) => (
                    <SecondFactorForm
                        offer={offer}
                        onAccepted={(codesLeft) => {
                            setUsedRecovery(codesLeft !== undefined);
                            refresh();
                        }}
                        onOver={() => { setOver(true); }}
                        onChanged={refresh}
                    />
                ),
                open: () => (
                    <Stack gap="stack">
                        <ConfirmStepUp keepOpen={usedRecovery} />
                        {usedRecovery ? <Callout tone="attention">{t("recoveryUsed")}</Callout> : null}
                    </Stack>
                ),
                startAgain: () => <Callout tone="danger">{t("ended")}</Callout>,
                blocked: () => <Callout tone="danger">{t("failed")}</Callout>,
            });
    }
}
