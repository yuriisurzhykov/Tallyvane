"use client";

import { useState } from "react";
import { Button } from "frontend-shared/ui/button";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Switch } from "frontend-shared/ui/switch";
import { Text } from "frontend-shared/ui/text";
import type { useAdminAuthenticationStrings } from "@/app/i18n";
import { SettingsGroup } from "settings-kit/widgets/settings-group";
import { SettingsItem } from "settings-kit/entities/settings-item";
import { SettingsFormActions } from "settings-kit/widgets/settings-form";
import { useOptionalSettingsUnsavedChanges } from "settings-kit/features/settings-navigation";
import { SchemeEditor, schemeTitle } from "./SchemeEditor";
import { PolicyConflictPanel } from "./PolicyConflictPanel";

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

export type SchemeDraft = Omit<Scheme, "id">;
type ScreenState =
    | { kind: "overview" }
    | { kind: "action"; action: Action }
    | { kind: "scheme"; action: Action; schemeId: string | null; draft: SchemeDraft };

export type Translate = ReturnType<typeof useAdminAuthenticationStrings>;
export type TokenLabels = Record<TokenKind, string>;
export type ActionLabels = Record<Action, string>;

const actions: Action[] = ["SIGN_IN", "CHANGE_PRIMARY_CREDENTIAL", "MANAGE_SECOND_FACTORS"];
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
    readonly isDirty: boolean;
    readonly onCancelDraft: () => void;
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
        t, tokenLabels, actionLabels, policy, isDirty, onCancelDraft, busy, policyIssue, validationError, conflict,
        onUpdate, onAdd, onRequestRemove, onSave, onResolveConflict,
    } = props;
    const { screen, setScreen, visibleSchemes, openAction, beginAdd, beginEdit, updateDraft, saveScheme } =
        usePolicyScreen(policy, onUpdate, onAdd);
    const policyInvalid = policyIssue !== null || policy.schemes.some(scheme =>
        scheme.requiredTokens.length === 0 || !Number.isInteger(scheme.assuranceRank) || scheme.assuranceRank < 1,
    );
    const canSave = !policyInvalid && conflict === null && screen.kind !== "scheme";

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

        <Stack gap="inline">
            <Stack gap="inline-tight">
                <Text variant="bodyStrong">{ t("policyVersion", { version: policy.version }) }</Text>
                <Text variant="small" color="secondary">{ t("draftOnlyUntilSave") }</Text>
                { policyIssue && <Text variant="small" role="alert" tone="danger">{ policyIssue }</Text> }
            </Stack>
            <SettingsFormActions
                isDirty={isDirty}
                isSaving={busy}
                canSave={canSave}
                onSave={onSave}
                onCancel={onCancelDraft}
                labels={{ save: t("savePolicy"), cancel: t("cancel"), saving: t("savingPolicy") }}
            />
        </Stack>
    </Stack>;
}

function usePolicyScreen(policy: Policy, onUpdate: AuthenticationPolicyEditorProps["onUpdate"],
    onAdd: AuthenticationPolicyEditorProps["onAdd"]) {
    const [screen, setScreen] = useState<ScreenState>({ kind: "overview" });
    const action = screen.kind === "overview" ? null : screen.action;
    const visibleSchemes = action ? policy.schemes.filter(scheme => scheme.action === action) : [];
    const schemeDraftDirty = screen.kind === "scheme" && (
        screen.schemeId === null ||
        !sameSchemeDraft(screen.draft, policy.schemes.find(scheme => scheme.id === screen.schemeId))
    );
    useOptionalSettingsUnsavedChanges(schemeDraftDirty);

    const openAction = (nextAction: Action) => { setScreen({ kind: "action", action: nextAction }); };

    const beginAdd = () => {
        if (!action) return;
        setScreen({
            kind: "scheme",
            action,
            schemeId: null,
            draft: { action, requiredTokens: ["PASSWORD"], assuranceRank: 1, enabled: true },
        });
    };

    const beginEdit = (scheme: Scheme) => { setScreen({
        kind: "scheme",
        action: scheme.action,
        schemeId: scheme.id,
        draft: { action: scheme.action, requiredTokens: [...scheme.requiredTokens], assuranceRank: scheme.assuranceRank, enabled: scheme.enabled },
    }); };

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

    return { screen, setScreen, visibleSchemes, openAction, beginAdd, beginEdit, updateDraft, saveScheme };
}

function sameSchemeDraft(draft: SchemeDraft, saved: Scheme | undefined): boolean {
    return saved?.action === draft.action && saved.assuranceRank === draft.assuranceRank &&
        saved.enabled === draft.enabled && saved.requiredTokens.length === draft.requiredTokens.length &&
        draft.requiredTokens.every((token, index) => token === saved.requiredTokens[index]);
}

interface ActionOverviewProps {
    readonly t: Translate;
    readonly actionLabels: ActionLabels;
    readonly schemes: Scheme[];
    readonly disabled: boolean;
    readonly onOpen: (action: Action) => void;
}

function ActionOverview({ t, actionLabels, schemes, disabled, onOpen }: ActionOverviewProps) {
    return <SettingsGroup title={t("actionOverviewTitle")} description={t("actionOverviewHelp")}>
        { actions.map(action => <SettingsItem
            key={action}
            label={actionLabels[action]}
            description={t(actionDescriptions[action])}
            control={(
                <Row gap="inline" className="flex-wrap items-center justify-end">
                    <Text variant="small" color="muted">
                        { t("actionSchemeCount", { count: schemes.filter(scheme => scheme.action === action).length }) }
                    </Text>
                    <Button tone="neutral" size="sm" disabled={ disabled } onClick={ () => { onOpen(action); } }>
                        { t("configureAction") }
                    </Button>
                </Row>
            )}
        />) }
    </SettingsGroup>;
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
        <SettingsGroup title={actionLabel} description={t("actionSchemesHelp")}>
            { schemes.length === 0
                ? <Text variant="body" color="secondary">{ t("noPaths") }</Text>
                : schemes.map(scheme => <SchemeCard
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
        </SettingsGroup>
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
    return <SettingsItem
        label={title}
        description={t("pathSummary", { rank: scheme.assuranceRank, action: actionLabel })}
        control={(
            <Stack gap="inline">
                <Row gap="inline" className="items-center">
                    <Text variant="small">{ t(scheme.enabled ? "policyEnabled" : "policyDisabled") }</Text>
                    <Switch
                        aria-label={ t("schemeEnabledLabel", { action: actionLabel, path: title }) }
                        checked={ scheme.enabled }
                        disabled={ busy }
                        onCheckedChange={ onEnabledChange }
                    />
                </Row>
                <Row gap="inline" className="flex-wrap">
                <Button tone="neutral" size="sm" disabled={ busy } onClick={ onEdit}>{ t("editPath") }</Button>
                <Button tone="danger" size="sm" disabled={ busy } onClick={ onRequestRemove}>{ t("removePath") }</Button>
                </Row>
            </Stack>
        )}
    />;
}

