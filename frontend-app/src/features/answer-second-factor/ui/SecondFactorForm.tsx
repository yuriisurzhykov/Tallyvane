"use client";

import { Button } from "frontend-shared/ui/button";
import { Callout } from "frontend-shared/ui/callout";
import { Field } from "frontend-shared/ui/field";
import { Form } from "frontend-shared/ui/form";
import { Input } from "frontend-shared/ui/input";
import { LiveRegion } from "frontend-shared/ui/live-region";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import type { CodeOffer } from "@/entities/sign-in";
import { useStrings } from "@/shared/i18n";
import { useCodeEntry } from "../model/useCodeEntry";

export interface SecondFactorFormProps {
    readonly offer: CodeOffer;
    /** The code was right. `codesLeft` is told when it was a recovery code, which retires the authenticator. */
    readonly onAccepted: (codesLeft: number | undefined) => void;
    /** The attempt is over; the person begins again. */
    readonly onOver: () => void;
    /** The attempt changed under the request: the page asks where it stands. */
    readonly onChanged: () => void;
}

/**
 * The field for the second step of a sign-in or a confirmation: six digits from the app, or a recovery code
 * for a person who cannot reach it. Nothing is sent by itself on the last digit and nothing short of a whole
 * code is sent at all, because every wrong code lengthens the pause. What the server decides (right, wrong, a
 * pause, over, changed) comes back as a `CodeAnswer`, and this form only says what each looks like.
 */
export function SecondFactorForm({ offer, onAccepted, onOver, onChanged }: SecondFactorFormProps) {
    const t = useStrings("secondFactor");
    const entry = useCodeEntry(offer, {
        onAccepted,
        onOver,
        onChanged,
        announce: (seconds) => t("pausedAnnouncement", { seconds }),
    });
    const app = entry.kind === "totp";

    return (
        <Form onFormSubmit={entry.send}>
            <Field
                label={app ? t("codeLabel") : t("recoveryLabel")}
                description={app ? t("codeHint") : t("recoveryHint")}
                {...(entry.mistake === "wrong" ? { error: app ? t("wrongCode") : t("wrongRecovery") } : {})}
            >
                <Input
                    name="code"
                    value={entry.text}
                    onChange={(event) => { entry.type(event.target.value); }}
                    autoFocus
                    autoComplete={app ? "one-time-code" : "off"}
                    inputMode={app ? "numeric" : "text"}
                    autoCapitalize={app ? "off" : "characters"}
                    spellCheck={false}
                    disabled={entry.working}
                />
            </Field>
            {entry.mistake === "failed" ? <Callout tone="danger">{t("failed")}</Callout> : null}
            {entry.secondsLeft > 0 ? <Text variant="small" tone="attention">{t("paused", { seconds: entry.secondsLeft })}</Text> : null}
            <LiveRegion>{entry.announcement}</LiveRegion>
            <Stack gap="stack-tight">
                <Button type="submit" tone="primary" size="lg" loading={entry.working} disabled={!entry.canSend && !entry.working} className="w-full">
                    {t("submit")}
                </Button>
                {offer.offers(entry.other) ? (
                    <Button type="button" tone="ghost" size="sm" disabled={entry.working} onClick={() => { entry.switchTo(entry.other); }}>
                        {app ? t("useRecovery") : t("useApp")}
                    </Button>
                ) : null}
            </Stack>
        </Form>
    );
}
