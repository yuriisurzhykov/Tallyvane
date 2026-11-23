"use client";

import { Button } from "frontend-shared/ui/button";
import { Callout } from "frontend-shared/ui/callout";
import { Field } from "frontend-shared/ui/field";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { TextArea } from "frontend-shared/ui/text-area";
import { useStrings } from "@/shared/i18n";
import type { SavingState } from "../model/useSaveVersion";

export interface SaveBarProps {
    readonly state: SavingState;
    readonly comment: string;
    readonly onComment: (comment: string) => void;
    readonly onSave: () => void;
    readonly onCancel: () => void;
    /** What the server found wrong that no field of the form shows itself, in words. */
    readonly messages: readonly string[];
    /** What the server said is wrong with the reason, in words. */
    readonly commentError?: string;
    /** The name of the reason field when the form numbers its parts ("3. Reason"). */
    readonly label?: string;
}

/**
 * The foot of the form that keeps a new version: why it was made, what the server refused, and the buttons.
 * It stands outside the part of the drawer that scrolls, so the reason and the save are never out of reach.
 */
export function SaveBar({ state, comment, onComment, onSave, onCancel, messages, commentError, label }: SaveBarProps) {
    const t = useStrings("save");
    const saving = state.status === "saving";

    return (
        <Stack gap="stack" className="border-t border-border-subtle pt-stack">
            {messages.length > 0 ? (
                <Callout tone="danger">
                    <Stack gap="stack-tight">
                        <Text variant="bodyStrong">{t("violations")}</Text>
                        {messages.map((message) => (
                            <Text key={message} variant="body">{message}</Text>
                        ))}
                    </Stack>
                </Callout>
            ) : null}
            {state.status === "failed" ? <Callout tone="danger">{t("failed")}</Callout> : null}
            <Field label={label ?? t("commentLabel")} description={t("commentHint")} {...(commentError !== undefined ? { error: commentError } : {})}>
                <TextArea value={comment} rows={2} onChange={(event) => { onComment(event.target.value); }} />
            </Field>
            <Row gap="inline" className="w-full">
                <Button tone="neutral" className="flex-1" disabled={saving} onClick={onCancel}>{t("cancel")}</Button>
                <Button tone="primary" className="flex-1" loading={saving} onClick={onSave}>{t("save")}</Button>
            </Row>
        </Stack>
    );
}
