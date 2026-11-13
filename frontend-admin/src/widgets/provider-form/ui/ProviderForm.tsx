"use client";

import { useState, type ChangeEvent } from "react";
import { Button } from "frontend-shared/ui/button";
import { Field } from "frontend-shared/ui/field";
import { Input } from "frontend-shared/ui/input";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Heading } from "frontend-shared/ui/heading";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import { PRESETS, type ModelProvider, type ModelProviderSent } from "@/entities/model-provider";
import { useStrings } from "@/shared/i18n";

export interface ProviderFormProps {
    /** The provider being changed, or undefined for a new one. */
    readonly editing?: ModelProvider | undefined;
    readonly busy: boolean;
    readonly onSave: (sent: ModelProviderSent) => void;
    /** Offered only for a provider that exists. */
    readonly onRemove?: () => void;
    readonly onCancel: () => void;
}

function draftOf(editing: ModelProvider | undefined): ModelProviderSent {
    if (editing === undefined) {
        return { name: "", kind: "openai_compatible", baseUrl: "", key: "", fast: "", careful: "", priceIn: 0, priceOut: 0 };
    }
    const { name, kind, baseUrl, fast, careful, priceIn, priceOut } = editing;
    return { name, kind, baseUrl: baseUrl ?? "", key: "", fast, careful, priceIn, priceOut };
}

/** Adds a provider or changes one. The key is never shown back: an empty key keeps the one kept. */
export function ProviderForm({ editing, busy, onSave, onRemove, onCancel }: ProviderFormProps) {
    const t = useStrings("models");
    const [sent, setSent] = useState<ModelProviderSent>(() => draftOf(editing));
    const set = (change: Partial<ModelProviderSent>) => { setSent((now) => ({ ...now, ...change })); };
    const text = (field: "name" | "baseUrl" | "key" | "fast" | "careful") => ({
        value: sent[field] ?? "",
        onChange: (event: ChangeEvent<HTMLInputElement>) => { set({ [field]: event.target.value }); },
    });
    const price = (field: "priceIn" | "priceOut") => ({
        type: "number" as const,
        min: 0,
        step: "0.01",
        value: String(sent[field]),
        onChange: (event: ChangeEvent<HTMLInputElement>) => { set({ [field]: Number(event.target.value) }); },
    });
    const save = () => { onSave({ ...sent, baseUrl: sent.baseUrl === "" || sent.baseUrl === undefined ? null : sent.baseUrl }); };

    return (
        <Surface variant="elevated" className="p-group-gap">
            <Stack gap="group-gap">
                <Heading level={2} variant="title3">{editing === undefined ? t("addTitle") : t("editTitle", { name: editing.name })}</Heading>
                {editing === undefined ? (
                    <Stack gap="inline-tight">
                        <Text variant="overline" color="secondary">{t("startFrom")}</Text>
                        <Row gap="inline" className="flex-wrap">
                            {PRESETS.map((preset) => (
                                <Button key={preset.name} tone="neutral" size="sm" onClick={() => { set({ name: preset.name, kind: preset.kind, baseUrl: preset.baseUrl }); }}>
                                    {preset.name}
                                </Button>
                            ))}
                        </Row>
                    </Stack>
                ) : null}
                <Stack gap="stack">
                    <Field label={t("name")}><Input {...text("name")} /></Field>
                    <Field label={t("baseUrl")} description={sent.kind === "anthropic" ? t("baseUrlAnthropic") : t("baseUrlOpenAi")}>
                        <Input {...text("baseUrl")} />
                    </Field>
                    <Field label={t("key")} description={editing === undefined ? t("keyNew") : t("keyKeep", { hint: editing.keyHint })}>
                        <Input type="password" autoComplete="off" {...text("key")} />
                    </Field>
                </Stack>
                <Row gap="stack" className="items-start">
                    <Stack gap="stack" className="flex-1"><Field label={t("fast")}><Input {...text("fast")} /></Field></Stack>
                    <Stack gap="stack" className="flex-1"><Field label={t("careful")}><Input {...text("careful")} /></Field></Stack>
                </Row>
                <Row gap="stack" className="items-start">
                    <Stack gap="stack" className="flex-1"><Field label={t("priceIn")}><Input {...price("priceIn")} /></Field></Stack>
                    <Stack gap="stack" className="flex-1"><Field label={t("priceOut")}><Input {...price("priceOut")} /></Field></Stack>
                </Row>
                <Row gap="inline" className="flex-wrap justify-between">
                    <Row gap="inline">
                        <Button tone="primary" loading={busy} onClick={save}>{t("save")}</Button>
                        <Button tone="neutral" onClick={onCancel}>{t("cancel")}</Button>
                    </Row>
                    {onRemove !== undefined ? <RemoveButton onRemove={onRemove} /> : null}
                </Row>
            </Stack>
        </Surface>
    );
}

/** Removing asks twice: the first press only asks, and the solid red shows up only for the second. */
function RemoveButton({ onRemove }: { readonly onRemove: () => void }) {
    const t = useStrings("models");
    const [sure, setSure] = useState(false);
    return sure ? (
        <Button tone="danger" onClick={onRemove}>{t("removeSure")}</Button>
    ) : (
        <Button tone="ghost" onClick={() => { setSure(true); }}>{t("removeAsk")}</Button>
    );
}
