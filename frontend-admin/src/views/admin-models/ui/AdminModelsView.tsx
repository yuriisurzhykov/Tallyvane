"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { ProblemError, useApi } from "frontend-shared/api";
import { AppShell } from "frontend-shared/ui/app-shell";
import { Button } from "frontend-shared/ui/button";
import { Callout } from "frontend-shared/ui/callout";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { ModelProviders, type ModelProvider, type ModelProviderSent, type ModelsScreen } from "@/entities/model-provider";
import { useStrings } from "@/shared/i18n";
import { adminNavItems } from "@/app/navigation";
import { ProviderForm } from "@/widgets/provider-form";
import { CostTable } from "./CostTable";
import { ProviderCards } from "./ProviderCards";

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
            <Stack gap="section-gap" className="max-w-(--layout-column-wide-max-width)">
                <Text variant="body" color="secondary">{t("lead")}</Text>
                {problem !== undefined ? <Callout tone="danger">{problem}</Callout> : null}
                {editing !== undefined ? (
                    <ProviderForm
                        key={editing.id}
                        editing={editing.provider}
                        busy={busy}
                        onSave={save}
                        onRemove={() => void run(() => providers.remove(editing.id))}
                        onCancel={() => { setEditing(undefined); }}
                    />
                ) : null}
                <ProviderCards
                    providers={screen?.providers ?? []}
                    onEdit={(provider) => { setEditing({ id: provider.id, provider }); }}
                    onChoose={(provider) => void run(() => providers.choose(provider.id))}
                />
                <CostTable costs={screen?.costs ?? []} />
            </Stack>
        </AppShell>
    );
}
