"use client";

import { useState } from "react";
import { Button } from "frontend-shared/ui/button";
import { Field } from "frontend-shared/ui/field";
import { Input } from "frontend-shared/ui/input";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import { PRESETS, type ModelProvider, type ModelProviderSent } from "@/entities/model-provider";
import { useStrings } from "@/shared/i18n";

export interface ProviderFormProps {
    /** The provider being changed, or undefined for a new one. */
    readonly editing?: ModelProvider | undefined;
    readonly busy: boolean;
    readonly onSave: (sent: ModelProviderSent) => void;
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
export function ProviderForm({ editing, busy, onSave, onCancel }: ProviderFormProps) {
    const t = useStrings("models");
    const [sent, setSent] = useState<ModelProviderSent>(() => draftOf(editing));
    const set = (change: Partial<ModelProviderSent>) => { setSent((now) => ({ ...now, ...change })); };

    return (
        <Surface variant="elevated">
            <Stack gap="stack">
                <Text variant="title3">{editing === undefined ? t("addTitle") : t("editTitle", { name: editing.name })}</Text>
                <Row gap="inline-tight">
                    {PRESETS.map((preset) => (
                        <Button key={preset.name} tone="ghost" size="sm" onClick={() => { set({ name: preset.name, kind: preset.kind, baseUrl: preset.baseUrl }); }}>
                            {preset.name}
                        </Button>
                    ))}
                </Row>
                <Field label={t("name")}>
                    <Input value={sent.name} onChange={(event) => { set({ name: event.target.value }); }} />
                </Field>
                <Field label={t("baseUrl")} description={sent.kind === "anthropic" ? t("baseUrlAnthropic") : t("baseUrlOpenAi")}>
                    <Input value={sent.baseUrl ?? ""} onChange={(event) => { set({ baseUrl: event.target.value }); }} />
                </Field>
                <Field label={t("key")} description={editing === undefined ? t("keyNew") : t("keyKeep", { hint: editing.keyHint })}>
                    <Input type="password" autoComplete="off" value={sent.key ?? ""} onChange={(event) => { set({ key: event.target.value }); }} />
                </Field>
                <Row gap="inline">
                    <Field label={t("fast")}>
                        <Input value={sent.fast} onChange={(event) => { set({ fast: event.target.value }); }} />
                    </Field>
                    <Field label={t("careful")}>
                        <Input value={sent.careful} onChange={(event) => { set({ careful: event.target.value }); }} />
                    </Field>
                </Row>
                <Row gap="inline">
                    <Field label={t("priceIn")}>
                        <Input type="number" min={0} step="0.01" value={String(sent.priceIn)} onChange={(event) => { set({ priceIn: Number(event.target.value) }); }} />
                    </Field>
                    <Field label={t("priceOut")}>
                        <Input type="number" min={0} step="0.01" value={String(sent.priceOut)} onChange={(event) => { set({ priceOut: Number(event.target.value) }); }} />
                    </Field>
                </Row>
                <Row gap="inline">
                    <Button tone="primary" loading={busy} onClick={() => { onSave({ ...sent, baseUrl: sent.baseUrl === "" || sent.baseUrl === undefined ? null : sent.baseUrl }); }}>
                        {t("save")}
                    </Button>
                    <Button tone="neutral" onClick={onCancel}>{t("cancel")}</Button>
                </Row>
            </Stack>
        </Surface>
    );
}
