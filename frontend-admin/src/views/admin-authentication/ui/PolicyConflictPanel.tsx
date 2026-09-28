"use client";

import { Button } from "frontend-shared/ui/button";
import { Panel } from "frontend-shared/ui/panel";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import type { Policy, Scheme, TokenLabels, ActionLabels, Translate } from "./AuthenticationPolicyEditor";
import { schemeTitle } from "./SchemeEditor";

interface PolicyConflictPanelProps {
    readonly t: Translate;
    readonly draft: Policy;
    readonly latest: Policy;
    readonly tokenLabels: TokenLabels;
    readonly actionLabels: ActionLabels;
    readonly onResolve: (resolution: "reload" | "replace") => void;
}

export function PolicyConflictPanel({ t, draft, latest, tokenLabels, actionLabels, onResolve }: PolicyConflictPanelProps) {
    const changes = policyChanges(draft, latest);
    return <Panel header={ <Text variant="bodyStrong">{ t("conflictTitle") }</Text> }>
        <Stack gap="stack">
            <Text variant="body">{ t("conflictHelp", { draftVersion: draft.version, latestVersion: latest.version }) }</Text>
            { changes.length === 0
                ? <Text variant="body" color="secondary">{ t("conflictNoSchemeChanges") }</Text>
                : <Stack gap="stack">
                    { changes.map(change => <Panel key={ change.id } variant="inset">
                        <Text variant="small" color="muted">{ actionLabels[schemeForChange(change).action] }</Text>
                        <Stack gap="stack" className="grid grid-cols-1 sm:grid-cols-2">
                            <Stack gap="inline-tight">
                                <Text variant="bodyStrong">{ t("yourDraft") }</Text>
                                <Text variant="body">{ change.draft ? schemeTitle(change.draft, tokenLabels) : t("pathRemoved") }</Text>
                                { change.draft && <Text variant="small" color="secondary">
                                    { t("rankAndState", { rank: change.draft.assuranceRank, state: t(change.draft.enabled ? "policyEnabled" : "policyDisabled") }) }
                                </Text> }
                            </Stack>
                            <Stack gap="inline-tight">
                                <Text variant="bodyStrong">{ t("latestPolicy") }</Text>
                                <Text variant="body">{ change.latest ? schemeTitle(change.latest, tokenLabels) : t("pathRemoved") }</Text>
                                { change.latest && <Text variant="small" color="secondary">
                                    { t("rankAndState", { rank: change.latest.assuranceRank, state: t(change.latest.enabled ? "policyEnabled" : "policyDisabled") }) }
                                </Text> }
                            </Stack>
                        </Stack>
                    </Panel>) }
                </Stack> }
            { draft.advancedAcknowledged !== latest.advancedAcknowledged && <Text variant="body" role="status">
                { t("emailRiskAckComparison", {
                    draft: t(draft.advancedAcknowledged ? "confirmed" : "notConfirmed"),
                    latest: t(latest.advancedAcknowledged ? "confirmed" : "notConfirmed"),
                }) }
            </Text> }
            <Row gap="inline" className="flex-wrap">
                <Button tone="neutral" type="button" onClick={ () => { onResolve("reload"); }}>
                    { t("discardDraftReload", { version: latest.version }) }
                </Button>
                <Button tone="danger" type="button" onClick={ () => { onResolve("replace"); }}>
                    { t("replaceLatestWithDraft", { version: latest.version }) }
                </Button>
            </Row>
        </Stack>
    </Panel>;
}

function schemeForChange(change: { draft?: Scheme; latest?: Scheme }): Scheme {
    const scheme = change.draft ?? change.latest;
    if (!scheme) throw new Error("A policy change must contain a draft or latest scheme");
    return scheme;
}

function policyChanges(draft: Policy, latest: Policy): { id: string; draft?: Scheme; latest?: Scheme }[] {
    const draftById = new Map(draft.schemes.map(scheme => [scheme.id, scheme]));
    const latestById = new Map(latest.schemes.map(scheme => [scheme.id, scheme]));
    const ids = new Set([...draftById.keys(), ...latestById.keys()]);
    return [...ids].flatMap(id => {
        const draftScheme = draftById.get(id);
        const latestScheme = latestById.get(id);
        if (draftScheme && latestScheme && schemesEqual(draftScheme, latestScheme)) return [];
        return [{ id, ...(draftScheme ? { draft: draftScheme } : {}), ...(latestScheme ? { latest: latestScheme } : {}) }];
    });
}

function schemesEqual(left: Scheme, right: Scheme): boolean {
    return left.action === right.action && left.assuranceRank === right.assuranceRank && left.enabled === right.enabled &&
        [...left.requiredTokens].sort().join("|") === [...right.requiredTokens].sort().join("|");
}

