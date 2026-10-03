"use client";

import { useState } from "react";
import { ProblemError, StepUpDeclined, useApi } from "frontend-shared/api";
import { Button } from "frontend-shared/ui/button";
import { Callout } from "frontend-shared/ui/callout";
import { Field } from "frontend-shared/ui/field";
import { Form } from "frontend-shared/ui/form";
import { Input } from "frontend-shared/ui/input";
import { Link } from "frontend-shared/ui/link";
import { QrCode } from "frontend-shared/ui/qr-code";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { RecoveryCodesPanel, SecondFactors, type RecoveryCodes, type TotpKey } from "@/entities/second-factor";
import { useStrings } from "@/shared/i18n";
import { FirstCode } from "../model/FirstCode";

export interface EnableTotpProps {
    /** The person already had TOTP and it was retired: the same steps, said as setting it up again. */
    readonly again: boolean;
    /** The set-up is over and the standing has changed, or may have: the screen reads it again. */
    readonly onFinished: () => void;
}

/** Every place the set-up can be. The key, the link, the QR code and the codes live in this state and nowhere else. */
type Step =
    | { readonly name: "resting" }
    | { readonly name: "starting" }
    | { readonly name: "key"; readonly key: TotpKey; readonly mistake: "none" | "wrong" | "failed" }
    | { readonly name: "confirming"; readonly key: TotpKey }
    | { readonly name: "codes"; readonly codes: RecoveryCodes }
    | { readonly name: "lost" }
    | { readonly name: "failed" };

/**
 * Turning TOTP on: ask for a key, show it once to scan or type, take the first code that proves the person
 * holds it, and show the ten recovery codes once. Asking for the key is a dangerous act, so the chain may ask
 * the person to prove who they are first; if they would rather not, the card goes quietly back to rest.
 */
export function EnableTotp({ again, onFinished }: EnableTotpProps) {
    const t = useStrings("enableTotp");
    const api = useApi();
    const [step, setStep] = useState<Step>({ name: "resting" });

    const start = async () => {
        setStep({ name: "starting" });
        try {
            setStep({ name: "key", key: await new SecondFactors(api).begin(), mistake: "none" });
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

    const confirm = async (key: TotpKey, code: FirstCode) => {
        setStep({ name: "confirming", key });
        const secondFactors = new SecondFactors(api);
        try {
            const confirmation = await secondFactors.confirm(code.digits());
            const next = await confirmation.when<Promise<Step>>({
                confirmed: (codes) => Promise.resolve({ name: "codes", codes }),
                wrong: () => Promise.resolve({ name: "key", key, mistake: "wrong" }),
                conflict: async () => ((await secondFactors.standing()).isActive() ? { name: "lost" } : { name: "failed" }),
            });
            setStep(next);
        } catch {
            setStep({ name: "key", key, mistake: "failed" });
        }
    };

    switch (step.name) {
        case "resting":
            return (
                <Row gap="inline">
                    <Button tone="primary" onClick={() => void start()}>{again ? t("setUpAgain") : t("turnOn")}</Button>
                </Row>
            );
        case "starting":
            return (
                <Row gap="inline">
                    <Button tone="primary" loading disabled>{again ? t("setUpAgain") : t("turnOn")}</Button>
                </Row>
            );
        case "key":
        case "confirming":
            return (
                <KeyStep
                    totpKey={step.key}
                    mistake={step.name === "key" ? step.mistake : "none"}
                    working={step.name === "confirming"}
                    onConfirm={(code) => void confirm(step.key, code)}
                    onEdit={() => { if (step.name === "key" && step.mistake !== "none") setStep({ name: "key", key: step.key, mistake: "none" }); }}
                    onCancel={() => { setStep({ name: "resting" }); }}
                />
            );
        case "codes":
            return <RecoveryCodesPanel codes={step.codes} onSaved={onFinished} />;
        case "lost":
            return (
                <Stack gap="stack">
                    <Callout tone="attention">{t("lost")}</Callout>
                    <Row gap="inline">
                        <Button tone="neutral" onClick={onFinished}>{t("lostContinue")}</Button>
                    </Row>
                </Stack>
            );
        case "failed":
            return (
                <Stack gap="stack">
                    <Callout tone="danger">{t("failed")}</Callout>
                    <Row gap="inline">
                        <Button tone="neutral" onClick={() => { setStep({ name: "resting" }); }}>{t("tryAgain")}</Button>
                    </Row>
                </Stack>
            );
    }
}

interface KeyStepProps {
    readonly totpKey: TotpKey;
    readonly mistake: "none" | "wrong" | "failed";
    readonly working: boolean;
    readonly onConfirm: (code: FirstCode) => void;
    /** The person changed the field: a mistake shown for the earlier code no longer applies, and an invalid field would block submitting. */
    readonly onEdit: () => void;
    readonly onCancel: () => void;
}

function KeyStep({ totpKey, mistake, working, onConfirm, onEdit, onCancel }: KeyStepProps) {
    const t = useStrings("enableTotp");
    const [text, setText] = useState("");
    const code = new FirstCode(text);

    return (
        <Stack gap="stack">
            <Stack gap="stack-tight">
                <Text variant="bodyStrong">{t("scanTitle")}</Text>
                <Text variant="small" color="secondary">{t("scanLead")}</Text>
            </Stack>
            <QrCode value={totpKey.link()} label={t("qrLabel")} className="w-48" />
            <Stack gap="stack-tight">
                <Text variant="small" color="secondary">{t("orType")}</Text>
                <Text variant="numeric">{totpKey.spaced()}</Text>
                <Link href={totpKey.link()}>{t("openInApp")}</Link>
            </Stack>
            <Form onFormSubmit={() => { if (code.isComplete() && !working) onConfirm(code); }}>
                <Field
                    label={t("codeLabel")}
                    description={t("codeHint")}
                    {...(mistake === "wrong" ? { error: t("wrongCode") } : {})}
                >
                    <Input
                        name="code"
                        value={text}
                        onChange={(event) => { setText(event.target.value); onEdit(); }}
                        autoComplete="one-time-code"
                        inputMode="numeric"
                        spellCheck={false}
                        disabled={working}
                    />
                </Field>
                {mistake === "failed" ? <Callout tone="danger">{t("failed")}</Callout> : null}
                <Row gap="inline" className="flex-wrap">
                    <Button type="submit" tone="primary" loading={working} disabled={!code.isComplete() && !working}>{t("confirm")}</Button>
                    <Button type="button" tone="ghost" disabled={working} onClick={onCancel}>{t("cancel")}</Button>
                </Row>
            </Form>
        </Stack>
    );
}
