"use client";

import { useState } from "react";
import { Button } from "frontend-shared/ui/button";
import { Callout } from "frontend-shared/ui/callout";
import { Checkbox } from "frontend-shared/ui/checkbox";
import { Grid } from "frontend-shared/ui/grid";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "@/shared/i18n";
import type { RecoveryCodes } from "../model/RecoveryCodes";
import { TextFile } from "../model/TextFile";

const FILE_NAME = "tallyvane-recovery-codes.txt";

export interface RecoveryCodesPanelProps {
    readonly codes: RecoveryCodes;
    /** The person says they have the codes somewhere safe and wants to go on. */
    readonly onSaved: () => void;
}

type Copy = "idle" | "copied" | "failed";

/**
 * The ten recovery codes, told once. They live in this component's props and nowhere else: not in storage,
 * not in a log, and gone when the person leaves the page. The way on is blocked until they say they have them.
 */
export function RecoveryCodesPanel({ codes, onSaved }: RecoveryCodesPanelProps) {
    const t = useStrings("recoveryCodes");
    const [copy, setCopy] = useState<Copy>("idle");
    const [saved, setSaved] = useState(false);

    const copyCodes = () => {
        navigator.clipboard.writeText(codes.asText()).then(
            () => { setCopy("copied"); },
            () => { setCopy("failed"); },
        );
    };

    return (
        <Stack gap="stack">
            <Stack gap="stack-tight">
                <Text variant="bodyStrong">{t("title")}</Text>
                <Text variant="small" color="secondary">{t("lead")}</Text>
            </Stack>
            <Surface variant="inset" className="p-stack">
                <Grid columns={2} gap="stack-tight">
                    {codes.each((code) => (
                        <Text key={code} variant="numeric">{code}</Text>
                    ))}
                </Grid>
            </Surface>
            <Row gap="inline" className="flex-wrap items-center">
                <Button tone="neutral" size="sm" onClick={copyCodes}>
                    {copy === "copied" ? t("copied") : t("copy")}
                </Button>
                <Button tone="neutral" size="sm" onClick={() => { new TextFile(FILE_NAME, codes.asText()).save(); }}>
                    {t("download")}
                </Button>
            </Row>
            {copy === "failed" ? <Callout tone="attention">{t("copyFailed")}</Callout> : null}
            <Row gap="inline" className="items-start">
                <Checkbox aria-labelledby="codes-saved-text" checked={saved} onCheckedChange={setSaved} />
                <Text id="codes-saved-text" variant="small" color="secondary">{t("saved")}</Text>
            </Row>
            <Row gap="inline">
                <Button tone="primary" disabled={!saved} onClick={onSaved}>{t("done")}</Button>
            </Row>
        </Stack>
    );
}
