"use client";

import { useState } from "react";
import { useApi } from "frontend-shared/api";
import { Button } from "frontend-shared/ui/button";
import { Tabs } from "frontend-shared/ui/tabs";
import {
    CLIENTS,
    LifetimeFigures,
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
import { adminNavGroups } from "@/app/navigation";
import { AdminShell } from "@/widgets/admin-shell";
import { LifetimesEditor } from "@/widgets/lifetimes-editor";
import { VersionHistory } from "@/widgets/version-history";

/**
 * How long a session may live for each kind of client: the versions kept, a new one started from any of them,
 * and putting one in force or rolling back to an older one. The server decides what is allowed.
 */
export function AdminLifetimesView() {
    const t = useStrings("lifetimes");
    const clients = useStrings("clients");
    const api = useApi();
    const words = useLifetimeWords();
    const consequenceWords = useLifetimeConsequenceWords();
    const [client, setClient] = useState<Client>("browser");
    const [editing, setEditing] = useState<LifetimeVersion | undefined>(undefined);
    const { state, refresh } = useLifetimeHistory(client);

    const startingPoint = state.status === "ready" ? state.value.startingPoint() : undefined;
    const inForce = state.status === "ready" ? state.value.inForce() : undefined;

    return (
        <AdminShell
            groups={adminNavGroups("/lifetimes")}
            title={t("title")}
            lead={t("lead")}
            action={
                <Button tone="primary" size="sm" disabled={startingPoint === undefined} onClick={() => { setEditing(startingPoint); }}>
                    {t("newVersion")}
                </Button>
            }
        >
            <Tabs.Root
                value={client}
                onValueChange={(picked) => {
                    const next = CLIENTS.find((candidate) => candidate === picked);
                    if (next !== undefined) setClient(next);
                }}
            >
                <Tabs.List aria-label={clients("label")}>
                    {CLIENTS.map((candidate) => (
                        <Tabs.Tab key={candidate} value={candidate}>{clients(candidate)}</Tabs.Tab>
                    ))}
                    <Tabs.Indicator />
                </Tabs.List>
                <Tabs.Panel value={client}>
                    <VersionHistory
                        state={mapLoaded(state, (history) => history.map((version) => version))}
                        refresh={refresh}
                        showSummary={(version) => <LifetimeFigures form={version.asForm()} />}
                        differences={(version, current) => version.changesFrom(current, words)}
                        activationFor={(version) => new LifetimeVersions(api).activation(client, version.number())}
                        describe={(consequences: LifetimeConsequences) => consequences.describe(consequenceWords)}
                        onNewFrom={setEditing}
                    />
                </Tabs.Panel>
            </Tabs.Root>
            <LifetimesEditor
                client={client}
                from={editing}
                inForce={inForce}
                onClose={() => { setEditing(undefined); }}
                onSaved={() => { setEditing(undefined); refresh(); }}
            />
        </AdminShell>
    );
}
