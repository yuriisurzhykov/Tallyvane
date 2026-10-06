"use client";

import { useState } from "react";
import { StepUpDeclined, useApi } from "frontend-shared/api";
import { Button } from "frontend-shared/ui/button";
import { Callout } from "frontend-shared/ui/callout";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { SecondFactors } from "@/entities/second-factor";
import { useStrings } from "@/shared/i18n";

export interface DisableTotpProps {
    /** TOTP is off: the screen reads the standing again. */
    readonly onDisabled: () => void;
}

type Step = "resting" | "asking" | "working" | "failed";

/**
 * Turns TOTP off, and with it the recovery codes. It asks once, in place (the product has no modal windows,
 * ADR-091), in front of the confirmation the server may ask for, not instead of it. A person who declines
 * that confirmation goes back to rest without an error: they said no, nothing broke.
 */
export function DisableTotp({ onDisabled }: DisableTotpProps) {
    const t = useStrings("disableTotp");
    const api = useApi();
    const [step, setStep] = useState<Step>("resting");

    const disable = async () => {
        setStep("working");
        try {
            await new SecondFactors(api).disable();
        } catch (failure) {
            setStep(failure instanceof StepUpDeclined ? "resting" : "failed");
            return;
        }
        onDisabled();
    };

    if (step === "resting") {
        return (
            <Row gap="inline">
                <Button tone="neutral" onClick={() => { setStep("asking"); }}>{t("turnOff")}</Button>
            </Row>
        );
    }

    return (
        <Stack gap="stack-tight">
            {step === "failed" ? <Callout tone="danger">{t("failed")}</Callout> : null}
            <Text variant="body">{t("ask")}</Text>
            <Row gap="inline" className="flex-wrap items-center">
                <Button tone="danger" size="sm" loading={step === "working"} onClick={() => void disable()}>{t("confirm")}</Button>
                <Button tone="ghost" size="sm" disabled={step === "working"} onClick={() => { setStep("resting"); }}>{t("cancel")}</Button>
            </Row>
        </Stack>
    );
}
