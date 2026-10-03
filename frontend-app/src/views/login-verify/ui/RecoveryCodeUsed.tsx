"use client";

import { Button } from "frontend-shared/ui/button";
import { Callout } from "frontend-shared/ui/callout";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "@/shared/i18n";

export interface RecoveryCodeUsedProps {
    readonly codesLeft: number;
    readonly onContinue: () => void;
    readonly onSetUp: () => void;
}

/**
 * What a person is told after signing in with a recovery code: it was spent, and spending it retired the
 * authenticator, so only the codes that are left work until TOTP is set up again. Without this notice a person
 * who lost their phone could carry on without knowing their protection now stands on a few written codes.
 */
export function RecoveryCodeUsed({ codesLeft, onContinue, onSetUp }: RecoveryCodeUsedProps) {
    const t = useStrings("recoveryUsed");
    const left = codesLeft === 0 ? t("noneLeft") : codesLeft === 1 ? t("oneLeft") : t("manyLeft", { count: codesLeft });
    return (
        <Stack gap="stack">
            <Callout tone="attention">
                <Stack gap="stack-tight">
                    <Text variant="bodyStrong">{t("used")}</Text>
                    <Text variant="small">{left}</Text>
                    <Text variant="small">{t("retired")}</Text>
                </Stack>
            </Callout>
            <Row gap="inline" className="flex-wrap">
                <Button tone="primary" onClick={onSetUp}>{t("setUp")}</Button>
                <Button tone="neutral" onClick={onContinue}>{t("continue")}</Button>
            </Row>
        </Stack>
    );
}
