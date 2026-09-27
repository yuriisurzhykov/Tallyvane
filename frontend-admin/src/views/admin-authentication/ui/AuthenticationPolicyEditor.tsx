"use client";

import { useState } from "react";
import { Button } from "frontend-shared/ui/button";
import { Checkbox } from "frontend-shared/ui/checkbox";
import { Collapsible } from "frontend-shared/ui/collapsible";
import { Field } from "frontend-shared/ui/field";
import { Fieldset } from "frontend-shared/ui/fieldset";
import { Input } from "frontend-shared/ui/input";
import { Panel } from "frontend-shared/ui/panel";
import { Row } from "frontend-shared/ui/row";
import { Select } from "frontend-shared/ui/select";
import { Stack } from "frontend-shared/ui/stack";
import { Switch } from "frontend-shared/ui/switch";
import { Text } from "frontend-shared/ui/text";
import type { useAdminAuthenticationStrings } from "@/app/i18n";

export type TokenKind = "PASSWORD" | "GOOGLE" | "EMAIL_SIGN_IN_CODE" | "TOTP" | "EMAIL_FACTOR_CODE";
export type Action = "SIGN_IN" | "CHANGE_PRIMARY_CREDENTIAL" | "MANAGE_SECOND_FACTORS";

export interface Scheme {
    id: string;
    action: Action;
    requiredTokens: TokenKind[];
    assuranceRank: number;
    enabled: boolean;
}

export interface Policy {
    version: number;
    schemes: Scheme[];
    advancedAcknowledged: boolean;
}

type SchemeDraft = Omit<Scheme, "id">;
type ScreenState =
    | { kind: "overview" }
    | { kind: "action"; action: Action }
    | { kind: "scheme"; action: Action; schemeId: string | null; draft: SchemeDraft };

export type Translate = ReturnType<typeof useAdminAuthenticationStrings>;
export type TokenLabels = Record<TokenKind, string>;
export type ActionLabels = Record<Action, string>;

const actions: Action[] = ["SIGN_IN", "CHANGE_PRIMARY_CREDENTIAL", "MANAGE_SECOND_FACTORS"];
const tokenKinds: TokenKind[] = ["PASSWORD", "GOOGLE", "EMAIL_SIGN_IN_CODE", "TOTP", "EMAIL_FACTOR_CODE"];
const primaryTokens: TokenKind[] = ["PASSWORD", "GOOGLE", "EMAIL_SIGN_IN_CODE"];
const factorTokens: TokenKind[] = ["TOTP", "EMAIL_FACTOR_CODE"];
const actionDescriptions = {
    SIGN_IN: "signInActionHelp",
    CHANGE_PRIMARY_CREDENTIAL: "changeCredentialActionHelp",
    MANAGE_SECOND_FACTORS: "manageFactorsActionHelp",
} as const satisfies Record<Action, string>;

export interface AuthenticationPolicyEditorProps {
    readonly t: Translate;
    readonly tokenLabels: TokenLabels;
    readonly actionLabels: ActionLabels;
    readonly policy: Policy;
    readonly busy: boolean;
    readonly policyIssue: string | null;
    readonly validationError: string | null;
    readonly conflict: Policy | null;
    readonly onUpdate: (id: string, patch: Partial<Scheme>) => void;
    readonly onAdd: (scheme: SchemeDraft) => void;
    readonly onRequestRemove: (scheme: Scheme) => void;
    readonly onSave: () => void;
    readonly onResolveConflict: (resolution: "reload" | "replace") => void;
}

export function AuthenticationPolicyEditor(props: AuthenticationPolicyEditorProps) {
    const {
        t, tokenLabels, actionLabels, policy, busy, policyIssue, validationError, conflict,
        onUpdate, onAdd, onRequestRemove, onSave, onResolveConflict,
    } = props;
    const [screen, setScreen] = useState<ScreenState>({ kind: "overview" });
    const action = screen.kind === "overview" ? null : screen.action;
    const visibleSchemes = action ? policy.schemes.filter(scheme => scheme.action === action) : [];

    const openAction = (nextAction: Action) => setScreen({ kind: "action", action: nextAction });

    const beginAdd = () => {
        if (!action) return;
        setScreen({
            kind: "scheme",
            action,
            schemeId: null,
            draft: { action, requiredTokens: ["PASSWORD"], assuranceRank: 1, enabled: true },
        });
    };

    const beginEdit = (scheme: Scheme) => setScreen({
        kind: "scheme",
        action: scheme.action,
        schemeId: scheme.id,
        draft: { action: scheme.action, requiredTokens: [...scheme.requiredTokens], assuranceRank: scheme.assuranceRank, enabled: scheme.enabled },
    });

    const updateDraft = (patch: Partial<SchemeDraft>) => {
        setScreen(current => current.kind === "scheme"
            ? { ...current, draft: { ...current.draft, ...patch } }
            : current);
    };

    const saveScheme = (draft: SchemeDraft, schemeId: string | null) => {
        if (schemeId) onUpdate(schemeId, draft);
        else onAdd(draft);
        setScreen({ kind: "action", action: draft.action });
    };

    const policyInvalid = policyIssue !== null || policy.schemes.some(scheme =>
        scheme.requiredTokens.length === 0 || !Number.isInteger(scheme.assuranceRank) || scheme.assuranceRank < 1,
    );

    return <Stack gap="stack">
        { conflict && <PolicyConflictPanel
            t={ t }
            draft={ policy }
            latest={ conflict }
            tokenLabels={ tokenLabels }
            actionLabels={ actionLabels }
            onResolve={ onResolveConflict }
        /> }

        { screen.kind === "overview" && <ActionOverview
            t={ t }
            actionLabels={ actionLabels }
            schemes={ policy.schemes }
            disabled={ busy || conflict !== null }
            onOpen={ openAction }
        /> }

        { screen.kind === "action" && <ActionSchemes
            t={ t }
            actionLabel={ actionLabels[screen.action] }
            schemes={ visibleSchemes }
            tokenLabels={ tokenLabels }
            busy={ busy || conflict !== null }
            onBack={ () => { setScreen({ kind: "overview" }); } }
            onAdd={ beginAdd }
            onEdit={ beginEdit }
            onEnabledChange={ (scheme, enabled) => { onUpdate(scheme.id, { enabled }); } }
            onRequestRemove={ onRequestRemove }
        /> }

        { screen.kind === "scheme" && <SchemeEditor
            t={ t }
            actionLabel={ actionLabels[screen.action] }
            tokenLabels={ tokenLabels }
            scheme={ screen.draft }
            editing={ screen.schemeId !== null }
            busy={ busy || conflict !== null }
            validationError={ validationError }
            onChange={ updateDraft }
            onCancel={ () => { setScreen({ kind: "action", action: screen.action }); } }
            onSave={ () => { saveScheme(screen.draft, screen.schemeId); } }
        /> }

        <Panel>
            <Stack gap="inline" className="flex flex-col sm:flex-row sm:items-center sm:justify-between">
                <Stack gap="inline-tight">
                    <Text variant="bodyStrong">{ t("policyVersion", { version: policy.version }) }</Text>
                    <Text variant="small" color="secondary">{ t("draftOnlyUntilSave") }</Text>
                    { policyIssue && <Text variant="small" role="alert" tone="danger">{ policyIssue }</Text> }
                </Stack>
                <Button
                    className="w-full sm:w-auto"
                    tone="primary"
                    loading={ busy }
                    disabled={ policyInvalid || conflict !== null || screen.kind === "scheme" }
                    onClick={ onSave }
                >
                    { t("savePolicy") }
                </Button>
            </Stack>
        </Panel>
    </Stack>;
}

interface ActionOverviewProps {
    readonly t: Translate;
    readonly actionLabels: ActionLabels;
    readonly schemes: Scheme[];
    readonly disabled: boolean;
    readonly onOpen: (action: Action) => void;
}

function ActionOverview({ t, actionLabels, schemes, disabled, onOpen }: ActionOverviewProps) {
    return <Stack gap="stack">
        <Stack gap="inline-tight">
            <Text variant="title2" as="h2">{ t("actionOverviewTitle") }</Text>
            <Text variant="body" color="secondary">{ t("actionOverviewHelp") }</Text>
        </Stack>
        <Stack gap="stack" className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3">
            { actions.map(action => <Panel key={ action }>
                <Stack gap="stack" className="h-full">
                    <Stack gap="inline-tight">
                        <Text variant="title3" as="h3">{ actionLabels[action] }</Text>
                        <Text variant="small" color="secondary">{ t(actionDescriptions[action]) }</Text>
                    </Stack>
                    <Row gap="inline" className="mt-auto flex-wrap items-center justify-between">
                        <Text variant="small" color="muted">
                            { t("actionSchemeCount", { count: schemes.filter(scheme => scheme.action === action).length }) }
                        </Text>
                        <Button tone="neutral" size="sm" disabled={ disabled } onClick={ () => { onOpen(action); } }>
                            { t("configureAction") }
                        </Button>
                    </Row>
                </Stack>
            </Panel>) }
        </Stack>
    </Stack>;
}

interface ActionSchemesProps {
    readonly t: Translate;
    readonly actionLabel: string;
    readonly schemes: Scheme[];
    readonly tokenLabels: TokenLabels;
    readonly busy: boolean;
    readonly onBack: () => void;
    readonly onAdd: () => void;
    readonly onEdit: (scheme: Scheme) => void;
    readonly onEnabledChange: (scheme: Scheme, enabled: boolean) => void;
    readonly onRequestRemove: (scheme: Scheme) => void;
}

function ActionSchemes({
    t, actionLabel, schemes, tokenLabels, busy, onBack, onAdd, onEdit, onEnabledChange, onRequestRemove,
}: ActionSchemesProps) {
    return <Stack gap="stack">
        <Button tone="ghost" className="self-start" disabled={ busy } onClick={ onBack}>
            { t("backToActions") }
        </Button>
        <Stack gap="inline-tight">
            <Text variant="title2" as="h2">{ actionLabel }</Text>
            <Text variant="body" color="secondary">{ t("actionSchemesHelp") }</Text>
        </Stack>
        { schemes.length === 0
            ? <Panel><Text variant="body" color="secondary">{ t("noPaths") }</Text></Panel>
            : <Stack gap="inline">
                { schemes.map(scheme => <SchemeCard
                    key={ scheme.id }
                    t={ t }
                    actionLabel={ actionLabel }
                    tokenLabels={ tokenLabels }
                    scheme={ scheme }
                    busy={ busy }
                    onEdit={ () => { onEdit(scheme); } }
                    onEnabledChange={ enabled => { onEnabledChange(scheme, enabled); } }
                    onRequestRemove={ () => { onRequestRemove(scheme); } }
                />) }
            </Stack> }
        <Button className="self-start" tone="primary" disabled={ busy } onClick={ onAdd }>
            { t("addPath") }
        </Button>
    </Stack>;
}

interface SchemeCardProps {
    readonly t: Translate;
    readonly actionLabel: string;
    readonly tokenLabels: TokenLabels;
    readonly scheme: Scheme;
    readonly busy: boolean;
    readonly onEdit: () => void;
    readonly onEnabledChange: (enabled: boolean) => void;
    readonly onRequestRemove: () => void;
}

function SchemeCard({ t, actionLabel, tokenLabels, scheme, busy, onEdit, onEnabledChange, onRequestRemove }: SchemeCardProps) {
    const title = schemeTitle(scheme, tokenLabels);
    return <Panel variant="inset">
        <Stack gap="inline">
            <Row gap="inline" className="flex-col items-start justify-between sm:flex-row sm:items-center">
                <Stack gap="inline-tight">
                    <Text variant="bodyStrong">{ title }</Text>
                    <Text variant="small" color="secondary">
                        { t("pathSummary", { rank: scheme.assuranceRank, action: actionLabel }) }
                    </Text>
                </Stack>
                <Row gap="inline" className="items-center">
                    <Text variant="small">{ t(scheme.enabled ? "policyEnabled" : "policyDisabled") }</Text>
                    <Switch
                        aria-label={ t("schemeEnabledLabel", { action: actionLabel, path: title }) }
                        checked={ scheme.enabled }
                        disabled={ busy }
                        onCheckedChange={ onEnabledChange }
                    />
                </Row>
            </Row>
            <Row gap="inline" className="flex-wrap">
                <Button tone="neutral" size="sm" disabled={ busy } onClick={ onEdit}>{ t("editPath") }</Button>
                <Button tone="danger" size="sm" disabled={ busy } onClick={ onRequestRemove}>{ t("removePath") }</Button>
            </Row>
        </Stack>
    </Panel>;
}

interface SchemeEditorProps {
    readonly t: Translate;
    readonly actionLabel: string;
    readonly tokenLabels: TokenLabels;
    readonly scheme: SchemeDraft;
    readonly editing: boolean;
    readonly busy: boolean;
    readonly validationError: string | null;
    readonly onChange: (patch: Partial<SchemeDraft>) => void;
    readonly onCancel: () => void;
    readonly onSave: () => void;
}

function SchemeEditor({
    t, actionLabel, tokenLabels, scheme, editing, busy, validationError, onChange, onCancel, onSave,
}: SchemeEditorProps) {
    const isSignIn = scheme.action === "SIGN_IN";
    const primary = isSignIn
        ? scheme.requiredTokens.find(token => primaryTokens.includes(token)) ?? "PASSWORD"
        : scheme.requiredTokens[0] ?? "PASSWORD";
    const extraTokens = scheme.requiredTokens.filter(token => token !== primary);
    const factor = extraTokens.find(token => factorTokens.includes(token));
    const validRank = Number.isInteger(scheme.assuranceRank) && scheme.assuranceRank > 0;
    const validSignIn = !isSignIn || (
        scheme.requiredTokens.filter(token => primaryTokens.includes(token)).length === 1 && extraTokens.length <= 1
    );
    const canSave = !busy && validRank && scheme.requiredTokens.length > 0 && validSignIn;

    const setPrimary = (value: string | null) => {
        if (!value || !tokenKinds.includes(value as TokenKind)) return;
        const remaining = scheme.requiredTokens.filter(token => token !== primary && token !== value);
        onChange({ requiredTokens: [value as TokenKind, ...remaining] });
    };

    const setFactor = (value: string | null) => {
        if (!value) return;
        onChange({ requiredTokens: [primary, ...(value === "none" ? [] : [value as TokenKind])] });
    };

    const toggleExtraToken = (token: TokenKind, checked: boolean) => {
        const remaining = extraTokens.filter(item => item !== token);
        onChange({ requiredTokens: [primary, ...(checked ? [...remaining, token] : remaining)] });
    };

    return <Stack gap="stack">
        <Button tone="ghost" className="self-start" disabled={ busy } onClick={ onCancel }>{ t("backToActionSchemes") }</Button>
        <Stack gap="inline-tight">
            <Text variant="title2" as="h2">{ t(editing ? "editPath" : "newPathTitle") }</Text>
            <Text variant="body" color="secondary">{ t("schemeEditorHelp", { action: actionLabel }) }</Text>
        </Stack>

        <Stack gap="stack" className="max-w-2xl">
            <Field label={ t(isSignIn ? "primaryMethod" : "startingProof") }>
                <Select.Root value={ primary } disabled={ busy } onValueChange={ setPrimary }>
                    <Select.Trigger>
                        <Select.Value>{ tokenLabels[primary] }</Select.Value>
                        <Select.Icon />
                    </Select.Trigger>
                    <Select.Popup>
                        { (isSignIn ? primaryTokens : tokenKinds).map(token => <Select.Item key={ token } value={ token }>
                            { tokenLabels[token] }
                        </Select.Item>) }
                    </Select.Popup>
                </Select.Root>
            </Field>

            { isSignIn
                ? <Field label={ t("additionalFactorOptional") }>
                    <Select.Root value={ factor ?? "none" } disabled={ busy } onValueChange={ setFactor }>
                        <Select.Trigger>
                            <Select.Value>{ factor ? tokenLabels[factor] : t("noAdditionalProof") }</Select.Value>
                            <Select.Icon />
                        </Select.Trigger>
                        <Select.Popup>
                            <Select.Item value="none">{ t("noAdditionalProof") }</Select.Item>
                            { factorTokens.map(token => <Select.Item key={ token } value={ token }>{ tokenLabels[token] }</Select.Item>) }
                        </Select.Popup>
                    </Select.Root>
                </Field>
                : <Collapsible.Root>
                    <Stack gap="inline">
                        <Collapsible.Trigger className="w-full justify-between">
                            { t("additionalProofsOptionalCount", { count: extraTokens.length }) }
                        </Collapsible.Trigger>
                        <Collapsible.Panel>
                            <Fieldset legend={ t("additionalProofsOptional") } disabled={ busy }>
                                <Stack gap="inline">
                                    { tokenKinds.filter(token => token !== primary).map(token => <Field key={ token } label={ tokenLabels[token] }>
                                        <Checkbox
                                            checked={ extraTokens.includes(token) }
                                            disabled={ busy }
                                            onCheckedChange={ checked => { toggleExtraToken(token, checked); } }
                                        />
                                    </Field>) }
                                </Stack>
                            </Fieldset>
                        </Collapsible.Panel>
                    </Stack>
                </Collapsible.Root> }

            <Panel variant="inset">
                <Stack gap="inline-tight">
                    <Text variant="bodyStrong">{ t("pathResult", { path: schemeTitle(scheme, tokenLabels) }) }</Text>
                    <Text variant="small" color="secondary">{ t("proofsAnded") }</Text>
                </Stack>
            </Panel>

            <Collapsible.Root>
                <Stack gap="inline">
                    <Collapsible.Trigger className="w-full justify-between">{ t("advancedSchemeSettings") }</Collapsible.Trigger>
                    <Collapsible.Panel>
                        <Field label={ t("assuranceRank") } description={ t("rankHelp") }>
                            <Input
                                type="number"
                                min={ 1 }
                                step={ 1 }
                                value={ scheme.assuranceRank }
                                disabled={ busy }
                                onChange={ event => { onChange({ assuranceRank: Number(event.target.value) }); } }
                            />
                        </Field>
                        { !validRank && <Text variant="small" role="alert" tone="danger">{ t("rankRequired") }</Text> }
                    </Collapsible.Panel>
                </Stack>
            </Collapsible.Root>

            { validationError && <Text variant="small" role="alert" tone="danger">{ validationError }</Text> }
            { !validSignIn && <Text variant="small" role="alert" tone="danger">{ t("signInCompositionError") }</Text> }
            <Row gap="inline" className="flex-wrap">
                <Button tone="neutral" disabled={ busy } onClick={ onCancel}>{ t("cancel") }</Button>
                <Button tone="primary" disabled={ !canSave } onClick={ onSave}>{ t("saveSchemeToDraft") }</Button>
            </Row>
        </Stack>
    </Stack>;
}

interface PolicyConflictPanelProps {
    readonly t: Translate;
    readonly draft: Policy;
    readonly latest: Policy;
    readonly tokenLabels: TokenLabels;
    readonly actionLabels: ActionLabels;
    readonly onResolve: (resolution: "reload" | "replace") => void;
}

function PolicyConflictPanel({ t, draft, latest, tokenLabels, actionLabels, onResolve }: PolicyConflictPanelProps) {
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

function schemeTitle(scheme: Pick<Scheme, "requiredTokens">, labels: TokenLabels): string {
    return tokenKinds.filter(token => scheme.requiredTokens.includes(token)).map(token => labels[token]).join(" + ");
}
