"use client";

import { useState } from "react";
import { useApi } from "frontend-shared/api";
import { AppShell } from "frontend-shared/ui/app-shell";
import { Button } from "frontend-shared/ui/button";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { Toggle } from "frontend-shared/ui/toggle";
import { ToggleGroup } from "frontend-shared/ui/toggle-group";
import {
    CLIENTS,
    LifetimeVersions,
    useLifetimeConsequenceWords,
    useLifetimeHistory,
    useLifetimeWords,
    type Client,
    type LifetimeConsequences,
    type LifetimeVersion,
} from "@/entities/lifetime-version";
import { useStrings } from "@/shared/i18n";
import { mapLoaded } from "@/shared/lib";
import { adminNavItems } from "@/app/navigation";
import { LifetimesEditor } from "@/widgets/lifetimes-editor";
import { VersionHistory } from "@/widgets/version-history";

/**
 * How long a session may live for each kind of client: the versions kept, a new one started from any of them,
 * and putting one in force or rolling back to an older one. The server decides what is allowed.
 */
export function AdminLifetimesView() {
    const t = useStrings("lifetimes");
    const nav = useStrings("nav");
    const clients = useStrings("clients");
    const api = useApi();
    const words = useLifetimeWords();
    const consequenceWords = useLifetimeConsequenceWords();
    const [client, setClient] = useState<Client>("browser");
    const [editing, setEditing] = useState<LifetimeVersion | undefined>(undefined);
    const { state, refresh } = useLifetimeHistory(client);

    const startingPoint = state.status === "ready" ? state.value.startingPoint() : undefined;

    return (
        <AppShell
            navItems={adminNavItems("/lifetimes")}
            title={t("title")}
            skipLinkLabel={nav("skipToContent")}
            actions={
                <Button tone="primary" size="sm" disabled={startingPoint === undefined} onClick={() => { setEditing(startingPoint); }}>
                    {t("newVersion")}
                </Button>
            }
        >
            <Stack gap="group-gap" className="max-w-4xl">
                <Text variant="body" color="secondary">{t("lead")}</Text>
                <ToggleGroup
                    aria-label={clients("label")}
                    value={[client]}
                    onValueChange={(picked) => {
                        const next = CLIENTS.find((candidate) => candidate !== client && picked.includes(candidate));
                        if (next !== undefined) setClient(next);
                    }}
                >
                    {CLIENTS.map((candidate) => (
                        <Toggle key={candidate} value={candidate}>{clients(candidate)}</Toggle>
                    ))}
                </ToggleGroup>
                <VersionHistory
                    state={mapLoaded(state, (history) => history.map((version) => version))}
                    refresh={refresh}
                    summarize={(version) => version.summary(words)}
                    activationFor={(version) => new LifetimeVersions(api).activation(client, version.number())}
                    describe={(consequences: LifetimeConsequences) => consequences.describe(consequenceWords)}
                    onNewFrom={setEditing}
                />
            </Stack>
            <LifetimesEditor
                client={client}
                from={editing}
                onClose={() => { setEditing(undefined); }}
                onSaved={() => { setEditing(undefined); refresh(); }}
            />
        </AppShell>
    );
}
