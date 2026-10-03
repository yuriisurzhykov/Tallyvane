"use client";

import { useState } from "react";
import { useApi } from "frontend-shared/api";
import { Button } from "frontend-shared/ui/button";
import { Row } from "frontend-shared/ui/row";
import { Text } from "frontend-shared/ui/text";
import { Devices } from "@/entities/device";
import { useStrings } from "@/shared/i18n";

export interface SignOutOtherDevicesButtonProps {
    /** How many other devices there are, so the question says how much it ends. */
    readonly others: number;
    readonly onSignedOut: () => void;
    readonly onFailed: () => void;
}

type Step = "resting" | "asking" | "signing-out";

/**
 * Signs out everywhere but here. It ends sessions the person cannot see being ended, so the button asks
 * once, in place: the product has no modal windows (ARCHITECTURE §12.9), and the question stays where the finger was.
 */
export function SignOutOtherDevicesButton({ others, onSignedOut, onFailed }: SignOutOtherDevicesButtonProps) {
    const t = useStrings("devices");
    const api = useApi();
    const [step, setStep] = useState<Step>("resting");

    const confirm = async () => {
        setStep("signing-out");
        try {
            await new Devices(api).signOutOthers();
        } catch {
            setStep("resting");
            onFailed();
            return;
        }
        setStep("resting");
        onSignedOut();
    };

    if (step === "resting") {
        return (
            <Button tone="neutral" onClick={() => { setStep("asking"); }}>
                {t("signOutOthers")}
            </Button>
        );
    }

    return (
        <Row gap="inline" className="flex-wrap items-center">
            <Text variant="body">
                {others === 1 ? t("confirmOne") : t("confirmMany", { count: others })}
            </Text>
            <Button tone="danger" size="sm" loading={step === "signing-out"} onClick={() => void confirm()}>
                {t("confirmYes")}
            </Button>
            <Button tone="ghost" size="sm" disabled={step === "signing-out"} onClick={() => { setStep("resting"); }}>
                {t("confirmCancel")}
            </Button>
        </Row>
    );
}
