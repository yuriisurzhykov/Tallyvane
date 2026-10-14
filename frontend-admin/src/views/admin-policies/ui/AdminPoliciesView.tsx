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
    PURPOSES,
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
import { adminNavItems } from "@/app/navigation";
import { PolicyEditor } from "@/widgets/policy-editor";
import { VersionHistory } from "@/widgets/version-history";

/**
 * The sign-in policy of each purpose: the versions kept, a new one started from any of them, and putting one in
 * force or rolling back to an older one. The server decides what is allowed; the screen shows what it said.
 */
export function AdminPoliciesView() {
    const t = useStrings("policies");
    const nav = useStrings("nav");
    const purposes = useStrings("purposes");
    const api = useApi();
    const words = usePolicyWords();
    const consequenceWords = usePolicyConsequenceWords();
    const [purpose, setPurpose] = useState<Purpose>("login");
    const [editing, setEditing] = useState<PolicyVersion | undefined>(undefined);
    const { state, refresh } = usePolicyHistory(purpose);

    const startingPoint = state.status === "ready" ? state.value.startingPoint() : undefined;

    return (
        <AppShell
            navItems={adminNavItems("/policies")}
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
                    aria-label={purposes("label")}
                    value={[purpose]}
                    onValueChange={(picked) => {
                        const next = PURPOSES.find((candidate) => candidate !== purpose && picked.includes(candidate));
                        if (next !== undefined) setPurpose(next);
                    }}
                    className="flex-wrap"
                >
                    {PURPOSES.map((candidate) => (
                        <Toggle key={candidate} value={candidate}>{purposes(candidate)}</Toggle>
                    ))}
                </ToggleGroup>
                <VersionHistory
                    state={mapLoaded(state, (history) => history.map((version) => version))}
                    refresh={refresh}
                    summarize={(version) => version.summary(words)}
                    activationFor={(version) => new PolicyVersions(api).activation(purpose, version.number())}
                    describe={(consequences: PolicyConsequences) => consequences.describe(consequenceWords)}
                    onNewFrom={setEditing}
                />
            </Stack>
            <PolicyEditor
                purpose={purpose}
                from={editing}
                onClose={() => { setEditing(undefined); }}
                onSaved={() => { setEditing(undefined); refresh(); }}
            />
        </AppShell>
    );
}
