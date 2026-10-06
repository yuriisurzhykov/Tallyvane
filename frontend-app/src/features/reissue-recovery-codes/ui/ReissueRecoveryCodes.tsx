"use client";

import { useState } from "react";
import { ProblemError, StepUpDeclined, useApi } from "frontend-shared/api";
import { Button } from "frontend-shared/ui/button";
import { Callout } from "frontend-shared/ui/callout";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { RecoveryCodesPanel, SecondFactors, type RecoveryCodes } from "@/entities/second-factor";
import { useStrings } from "@/shared/i18n";

export interface ReissueRecoveryCodesProps {
    /** The new codes have been seen, or there turned out to be nothing to reissue: the screen reads the standing again. */
    readonly onFinished: () => void;
}

type Step =
    | { readonly name: "resting" }
    | { readonly name: "asking" }
    | { readonly name: "working" }
    | { readonly name: "failed" }
    | { readonly name: "codes"; readonly codes: RecoveryCodes };

/**
 * Asks for ten new recovery codes, which replace every code the person had. It asks once, in place, before it
 * does; then shows the new codes once. Reissuing is a dangerous act, so the chain may first ask the person to
 * prove who they are, and a person who declines goes back to rest without an error.
 */
export function ReissueRecoveryCodes({ onFinished }: ReissueRecoveryCodesProps) {
    const t = useStrings("reissueCodes");
    const api = useApi();
    const [step, setStep] = useState<Step>({ name: "resting" });

    const reissue = async () => {
        setStep({ name: "working" });
        try {
            setStep({ name: "codes", codes: await new SecondFactors(api).reissue() });
        } catch (failure) {
            if (failure instanceof StepUpDeclined) {
                setStep({ name: "resting" });
            } else if (failure instanceof ProblemError && failure.hasStatus(409)) {
                onFinished();
            } else {
                setStep({ name: "failed" });
            }
        }
    };

    switch (step.name) {
        case "resting":
            return (
                <Row gap="inline">
                    <Button tone="neutral" onClick={() => { setStep({ name: "asking" }); }}>{t("ask")}</Button>
                </Row>
            );
        case "codes":
            return <RecoveryCodesPanel codes={step.codes} onSaved={onFinished} />;
        case "asking":
        case "working":
        case "failed":
            return (
                <Stack gap="stack-tight">
                    {step.name === "failed" ? <Callout tone="danger">{t("failed")}</Callout> : null}
                    <Text variant="body">{t("warning")}</Text>
                    <Row gap="inline" className="flex-wrap items-center">
                        <Button tone="primary" size="sm" loading={step.name === "working"} onClick={() => void reissue()}>{t("confirm")}</Button>
                        <Button tone="ghost" size="sm" disabled={step.name === "working"} onClick={() => { setStep({ name: "resting" }); }}>{t("cancel")}</Button>
                    </Row>
                </Stack>
            );
    }
}
