"use client";

import { useState } from "react";
import { useApi } from "frontend-shared/api";
import { Button } from "frontend-shared/ui/button";
import { Stack } from "frontend-shared/ui/stack";
import { Tabs } from "frontend-shared/ui/tabs";
import { Text } from "frontend-shared/ui/text";
import {
    PURPOSES,
    PolicyFlow,
    PolicyVersions,
    usePolicyConsequenceWords,
    usePolicyHistory,
    usePolicyWords,
    type PolicyConsequences,
    type PolicyVersion,
    type Purpose,
} from "@/entities/policy-version";
import { useStrings } from "@/shared/i18n";
import { mapLoaded } from "@/shared/lib";
import { adminNavGroups } from "@/app/navigation";
import { AdminShell } from "@/widgets/admin-shell";
import { PolicyEditor } from "@/widgets/policy-editor";
import { VersionHistory } from "@/widgets/version-history";

/**
 * The sign-in policy of each purpose: the versions kept, a new one started from any of them, and putting one in
 * force or rolling back to an older one. The server decides what is allowed; the screen shows what it said.
 */
export function AdminPoliciesView() {
    const t = useStrings("policies");
    const purposes = useStrings("purposes");
    const api = useApi();
    const words = usePolicyWords();
    const consequenceWords = usePolicyConsequenceWords();
    const [purpose, setPurpose] = useState<Purpose>("login");
    const [editing, setEditing] = useState<PolicyVersion | undefined>(undefined);
    const { state, refresh } = usePolicyHistory(purpose);

    const startingPoint = state.status === "ready" ? state.value.startingPoint() : undefined;
    const inForce = state.status === "ready" ? state.value.inForce() : undefined;

    return (
        <AdminShell
            groups={adminNavGroups("/policies")}
            title={t("title")}
            lead={t("lead")}
            action={
                <Button tone="primary" size="sm" disabled={startingPoint === undefined} onClick={() => { setEditing(startingPoint); }}>
                    {t("newVersion")}
                </Button>
            }
        >
            <Tabs.Root
                value={purpose}
                onValueChange={(picked) => {
                    const next = PURPOSES.find((candidate) => candidate === picked);
                    if (next !== undefined) setPurpose(next);
                }}
            >
                <Tabs.List aria-label={purposes("label")} className="max-w-full flex-wrap">
                    {PURPOSES.map((candidate) => (
                        <Tabs.Tab key={candidate} value={candidate}>{purposes(candidate)}</Tabs.Tab>
                    ))}
                    <Tabs.Indicator />
                </Tabs.List>
                <Tabs.Panel value={purpose}>
                    <VersionHistory
                        state={mapLoaded(state, (history) => history.map((version) => version))}
                        refresh={refresh}
                        showSummary={(version) => (
                            <Stack gap="stack">
                                <PolicyFlow form={version.asForm()} />
                                <Text variant="numeric" color="secondary">{version.asForm().limitsLine(words)}</Text>
                            </Stack>
                        )}
                        differences={(version, current) => version.changesFrom(current, words)}
                        activationFor={(version) => new PolicyVersions(api).activation(purpose, version.number())}
                        describe={(consequences: PolicyConsequences) => consequences.describe(consequenceWords)}
                        onNewFrom={setEditing}
                    />
                </Tabs.Panel>
            </Tabs.Root>
            <PolicyEditor
                purpose={purpose}
                from={editing}
                inForce={inForce}
                onClose={() => { setEditing(undefined); }}
                onSaved={() => { setEditing(undefined); refresh(); }}
            />
        </AdminShell>
    );
}
