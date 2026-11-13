"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { ProblemError, useApi } from "frontend-shared/api";
import { AppShell } from "frontend-shared/ui/app-shell";
import { Badge } from "frontend-shared/ui/badge";
import { Button } from "frontend-shared/ui/button";
import { Callout } from "frontend-shared/ui/callout";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import { ModelProviders, type ModelProvider, type ModelProviderSent, type ModelsScreen } from "@/entities/model-provider";
import { useStrings } from "@/shared/i18n";
import { adminNavItems } from "@/app/navigation";
import { ProviderForm } from "@/widgets/provider-form";

type Editing = { readonly id: string; readonly provider?: ModelProvider } | undefined;

/**
 * The model providers: add one with its key and prices, choose the one every call goes to, and compare what the
 * last 30 days cost by provider, model and spec.
 */
export function AdminModelsView() {
    const t = useStrings("models");
    const nav = useStrings("nav");
    const api = useApi();
    const providers = useMemo(() => new ModelProviders(api), [api]);
    const [screen, setScreen] = useState<ModelsScreen | undefined>(undefined);
    const [editing, setEditing] = useState<Editing>(undefined);
    const [busy, setBusy] = useState(false);
    const [problem, setProblem] = useState<string | undefined>(undefined);
    const failed = t("failed");

    const run = useCallback(async (change: () => Promise<ModelsScreen>) => {
        setBusy(true);
        setProblem(undefined);
        try {
            setScreen(await change());
            setEditing(undefined);
        } catch (failure) {
            setProblem(failure instanceof ProblemError ? (failure.detail() ?? failed) : failed);
        } finally {
            setBusy(false);
        }
    }, [failed]);

    useEffect(() => {
        providers.screen().then(setScreen, () => { setProblem(failed); });
    }, [providers, failed]);

    const save = (sent: ModelProviderSent) => {
        if (editing !== undefined) void run(() => providers.save(editing.id, sent));
    };

    return (
        <AppShell
            navItems={adminNavItems("/models")}
            title={t("title")}
            skipLinkLabel={nav("skipToContent")}
            actions={<Button tone="primary" size="sm" onClick={() => { setEditing({ id: crypto.randomUUID() }); }}>{t("add")}</Button>}
        >
            <Stack gap="group-gap" className="max-w-4xl">
                <Text variant="body" color="secondary">{t("lead")}</Text>
                {problem !== undefined ? <Callout tone="danger">{problem}</Callout> : null}
                {editing !== undefined ? (
                    <ProviderForm key={editing.id} editing={editing.provider} busy={busy} onSave={save} onCancel={() => { setEditing(undefined); }} />
                ) : null}
                <ProviderList
                    providers={screen?.providers ?? []}
                    onEdit={(provider) => { setEditing({ id: provider.id, provider }); }}
                    onChoose={(provider) => void run(() => providers.choose(provider.id))}
                    onRemove={(provider) => void run(() => providers.remove(provider.id))}
                />
                <CostList costs={screen?.costs ?? []} />
            </Stack>
        </AppShell>
    );
}

interface ProviderListProps {
    readonly providers: readonly ModelProvider[];
    readonly onEdit: (provider: ModelProvider) => void;
    readonly onChoose: (provider: ModelProvider) => void;
    readonly onRemove: (provider: ModelProvider) => void;
}

function ProviderList({ providers, onEdit, onChoose, onRemove }: ProviderListProps) {
    const t = useStrings("models");
    if (providers.length === 0) return <Text variant="body">{t("none")}</Text>;
    return (
        <Stack gap="stack">
            {providers.map((provider) => (
                <Surface key={provider.id} variant={provider.active ? "elevated" : "inset"}>
                    <Stack gap="stack-tight">
                        <Row gap="inline">
                            <Text variant="bodyStrong">{provider.name}</Text>
                            {provider.active ? <Badge tone="success">{t("inUse")}</Badge> : null}
                            <Text variant="small">{provider.baseUrl ?? "api.anthropic.com"}</Text>
                            <Text variant="small">{provider.keyHint}</Text>
                        </Row>
                        <Text variant="small">
                            {t("modelsLine", { fast: provider.fast, careful: provider.careful, priceIn: provider.priceIn, priceOut: provider.priceOut })}
                        </Text>
                        <Row gap="inline-tight">
                            {provider.active ? null : <Button tone="primary" size="sm" onClick={() => { onChoose(provider); }}>{t("use")}</Button>}
                            <Button tone="neutral" size="sm" onClick={() => { onEdit(provider); }}>{t("edit")}</Button>
                            <Button tone="danger" size="sm" onClick={() => { onRemove(provider); }}>{t("remove")}</Button>
                        </Row>
                    </Stack>
                </Surface>
            ))}
        </Stack>
    );
}

function CostList({ costs }: { readonly costs: ModelsScreen["costs"] }) {
    const t = useStrings("models");
    const total = costs.reduce((sum, line) => sum + line.dollars, 0);
    return (
        <Stack gap="stack-tight">
            <Text variant="title3">{t("costTitle", { total: total.toFixed(4) })}</Text>
            {costs.length === 0 ? <Text variant="body">{t("noCalls")}</Text> : null}
            {costs.map((line) => (
                <Text key={`${line.provider}|${line.model}|${line.spec}`} variant="small">
                    {t("costLine", {
                        provider: line.provider,
                        model: line.model,
                        spec: line.spec,
                        calls: line.calls,
                        remembered: line.remembered,
                        tokensIn: line.tokensIn,
                        tokensOut: line.tokensOut,
                        dollars: line.dollars.toFixed(4),
                    })}
                </Text>
            ))}
        </Stack>
    );
}
