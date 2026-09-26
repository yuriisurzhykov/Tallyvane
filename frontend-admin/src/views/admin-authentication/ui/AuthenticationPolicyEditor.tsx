"use client";

import { useState } from "react";
import { Button } from "frontend-shared/ui/button";
import { Checkbox } from "frontend-shared/ui/checkbox";
import { Field } from "frontend-shared/ui/field";
import { Fieldset } from "frontend-shared/ui/fieldset";
import { Input } from "frontend-shared/ui/input";
import { Panel } from "frontend-shared/ui/panel";
import { Radio } from "frontend-shared/ui/radio";
import { RadioGroup } from "frontend-shared/ui/radio-group";
import { Select } from "frontend-shared/ui/select";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { useAdminAuthenticationStrings } from "@/app/i18n";

export type TokenKind = "PASSWORD" | "GOOGLE" | "EMAIL_SIGN_IN_CODE" | "TOTP" | "EMAIL_FACTOR_CODE" | "BACKUP_CODE";
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

export type SchemeUpdate = Partial<Scheme>;
export type Translate = ReturnType<typeof useAdminAuthenticationStrings>;
export type TokenLabels = Record<TokenKind, string>;
export type ActionLabels = Record<Action, string>;

const tokenKinds: TokenKind[] = ["PASSWORD", "GOOGLE", "EMAIL_SIGN_IN_CODE", "TOTP", "EMAIL_FACTOR_CODE", "BACKUP_CODE"];
const primaryTokens = ["PASSWORD", "GOOGLE", "EMAIL_SIGN_IN_CODE"] as const satisfies readonly TokenKind[];
const factorTokens = ["TOTP", "EMAIL_FACTOR_CODE", "BACKUP_CODE"] as const satisfies readonly TokenKind[];
const steps = ["chooseAction", "chooseProofs", "chooseRank", "reviewPath"] as const;

type Step = 1 | 2 | 3 | 4;

export interface AuthenticationPolicyEditorProps {
    readonly t: Translate;
    readonly tokenLabels: TokenLabels;
    readonly actionLabels: ActionLabels;
    readonly policy: Policy;
    readonly busy: boolean;
    readonly policyIssue: string | null;
    readonly validationError: string | null;
    readonly conflict: Policy | null;
    readonly onUpdate: (id: string, patch: SchemeUpdate) => void;
    readonly onAdd: (action: Action) => string;
    readonly onRemove: (id: string) => void;
    readonly onRequestRemove: (scheme: Scheme) => void;
    readonly onSave: () => void;
    readonly onResolveConflict: (resolution: "reload" | "replace") => void;
}

export function AuthenticationPolicyEditor(props: AuthenticationPolicyEditorProps) {
    const {
        t, tokenLabels, actionLabels, policy, busy, policyIssue, validationError, conflict,
        onUpdate, onAdd, onRemove, onRequestRemove, onSave, onResolveConflict,
    } = props;
    const [action, setAction] = useState<Action>("SIGN_IN");
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [step, setStep] = useState<Step>(1);
    const [newSchemeIds, setNewSchemeIds] = useState<Set<string>>(() => new Set());
    const [showStartError, setShowStartError] = useState(false);
    const selected = policy.schemes.find(scheme => scheme.id === selectedId);
    const visibleSchemes = policy.schemes.filter(scheme => scheme.action === action);
    const invalid = policyIssue !== null || policy.schemes.some(scheme =>
        scheme.requiredTokens.length === 0 || !Number.isInteger(scheme.assuranceRank) || scheme.assuranceRank < 1,
    );

    const chooseAction = (value: string | null) => {
        if (!value || !["SIGN_IN", "CHANGE_PRIMARY_CREDENTIAL", "MANAGE_SECOND_FACTORS"].includes(value)) return;
        setAction(value as Action);
        setSelectedId(null);
        setStep(1);
        setShowStartError(false);
    };

    const add = () => {
        const id = onAdd(action);
        setNewSchemeIds(current => new Set(current).add(id));
        setSelectedId(id);
        setStep(2);
        setShowStartError(true);
    };

    const open = (scheme: Scheme) => {
        setSelectedId(scheme.id);
        setStep(2);
        setShowStartError(false);
    };

    const cancelNew = (id: string) => {
        onRemove(id);
        setNewSchemeIds(current => {
            const next = new Set(current);
            next.delete(id);
            return next;
        });
        setSelectedId(null);
        setStep(1);
        setShowStartError(false);
    };

    const complete = (id: string) => {
        setNewSchemeIds(current => {
            const next = new Set(current);
            next.delete(id);
            return next;
        });
        setSelectedId(null);
        setStep(1);
        setShowStartError(false);
    };

    return <Stack gap="stack">
        { conflict && <PolicyConflictPanel
            t={ t }
            draft={ policy }
            latest={ conflict }
            tokenLabels={ tokenLabels }
            actionLabels={ actionLabels }
            onResolve={ onResolveConflict }
        /> }
        <div className="grid grid-cols-1 gap-stack lg:grid-cols-[minmax(16rem,0.8fr)_minmax(0,1.2fr)]">
            <Panel header={ <Text variant="bodyStrong">{ t("protectedAction") }</Text> }>
                <Stack gap="stack">
                    <Field label={ t("action") }>
                        <Select.Root value={ action } disabled={ busy || conflict !== null } onValueChange={ chooseAction }>
                            <Select.Trigger>
                                <Select.Value>{ actionLabels[action] }</Select.Value>
                                <Select.Icon />
                            </Select.Trigger>
                            <Select.Popup>
                                { (["SIGN_IN", "CHANGE_PRIMARY_CREDENTIAL", "MANAGE_SECOND_FACTORS"] as const).map(value =>
                                    <Select.Item key={ value } value={ value }>{ actionLabels[value] }</Select.Item>,
                                ) }
                            </Select.Popup>
                        </Select.Root>
                    </Field>
                    <Text variant="body" color="secondary">{ t("actionListHelp") }</Text>
                    { visibleSchemes.length === 0
                        ? <Text variant="body" color="muted">{ t("noPaths") }</Text>
                        : <div className="flex flex-col gap-stack">
                            { visibleSchemes.map(scheme => <SchemeListItem
                                key={ scheme.id }
                                scheme={ scheme }
                                title={ schemeTitle(scheme, tokenLabels) }
                                isDraft={ newSchemeIds.has(scheme.id) }
                                actionLabel={ actionLabels[scheme.action] }
                                busy={ busy || conflict !== null }
                                t={ t }
                                onOpen={ () => { open(scheme); } }
                                onEnabledChange={ enabled => { onUpdate(scheme.id, { enabled }); } }
                                onCancel={ () => { cancelNew(scheme.id); } }
                            />) }
                        </div> }
                    <Button tone="primary" type="button" disabled={ busy || conflict !== null } onClick={ add }>
                        { t("addPath") }
                    </Button>
                </Stack>
            </Panel>
            <Panel>
                { validationError && <Text variant="body" role="alert" tone="danger">{ validationError }</Text> }
                { selected
                    ? <PathFlow
                        scheme={ selected }
                        tokenLabels={ tokenLabels }
                        actionLabels={ actionLabels }
                        t={ t }
                        step={ step }
                        busy={ busy || conflict !== null }
                        showStartError={ showStartError }
                        strongerPaths={ visibleSchemes.filter(other => other.id !== selected.id && other.enabled &&
                            other.assuranceRank > selected.assuranceRank) }
                        onStepChange={ setStep }
                        onUpdate={ patch => { onUpdate(selected.id, patch); } }
                        onStartErrorChange={ setShowStartError }
                        { ...(newSchemeIds.has(selected.id) ? { onCancelNew: () => { cancelNew(selected.id); } } : {}) }
                        onRequestRemove={ () => { onRequestRemove(selected); } }
                        onComplete={ () => { complete(selected.id); } }
                    />
                    : <PathIntro
                        action={ action }
                        actionLabel={ actionLabels[action] }
                        t={ t }
                        onAdd={ add }
                        disabled={ busy || conflict !== null }
                    /> }
            </Panel>
        </div>
        <Panel className="flex flex-wrap items-center justify-between gap-stack">
            <Stack gap="inline-tight">
                <Text variant="bodyStrong">{ t("policyVersion", { version: policy.version }) }</Text>
                <Text variant="small" color="secondary">{ t("draftOnlyUntilSave") }</Text>
                { policyIssue && <Text variant="body" role="alert" tone="danger">{ policyIssue }</Text> }
            </Stack>
            <Button tone="primary" loading={ busy } disabled={ invalid || conflict !== null } onClick={ onSave }>
                { t("savePolicy") }
            </Button>
        </Panel>
    </Stack>;
}

interface SchemeListItemProps {
    readonly scheme: Scheme;
    readonly title: string;
    readonly isDraft: boolean;
    readonly actionLabel: string;
    readonly busy: boolean;
    readonly t: Translate;
    readonly onOpen: () => void;
    readonly onEnabledChange: (enabled: boolean) => void;
    readonly onCancel: () => void;
}

function SchemeListItem({ scheme, title, isDraft, actionLabel, busy, t, onOpen, onEnabledChange, onCancel }: SchemeListItemProps) {
    return <Panel variant="inset" header={ <Text variant="bodyStrong">{ title }</Text> }>
        <Stack gap="inline">
            <Text variant="small" color="secondary">{ t("pathSummary", { rank: scheme.assuranceRank, action: actionLabel }) }</Text>
            <Text variant="small" color="secondary">{ t("pathStatus", { state: t(scheme.enabled ? "policyEnabled" : "policyDisabled") }) }</Text>
            <Field label={ t("pathEnabled") }>
                <Checkbox checked={ scheme.enabled } disabled={ busy } onCheckedChange={ onEnabledChange } />
            </Field>
            { isDraft && <Text variant="small" tone="attention">{ t("unfinishedPath") }</Text> }
            <div className="flex flex-wrap gap-inline">
                <Button tone="neutral" size="sm" type="button" disabled={ busy } onClick={ onOpen}>{ t("editPath") }</Button>
                { isDraft && <Button tone="ghost" size="sm" type="button" disabled={ busy } onClick={ onCancel}>
                    { t("cancelPath") }
                </Button> }
            </div>
        </Stack>
    </Panel>;
}

interface PathIntroProps {
    readonly action: Action;
    readonly actionLabel: string;
    readonly t: Translate;
    readonly disabled: boolean;
    readonly onAdd: () => void;
}

function PathIntro({ action, actionLabel, t, disabled, onAdd }: PathIntroProps) {
    return <Stack gap="stack">
        <Text variant="title2" as="h2">{ t("guidedEditorTitle") }</Text>
        <Text variant="body">{ t("guidedEditorHelp", { action: actionLabel }) }</Text>
        <Panel variant="inset">
            <Text variant="body" color="secondary">
                { action === "SIGN_IN" ? t("signInPathExample") : t("accountPathExample") }
            </Text>
        </Panel>
        <div><Button tone="primary" type="button" disabled={ disabled } onClick={ onAdd}>{ t("addPath") }</Button></div>
    </Stack>;
}

interface PathFlowProps {
    readonly scheme: Scheme;
    readonly tokenLabels: TokenLabels;
    readonly actionLabels: ActionLabels;
    readonly t: Translate;
    readonly step: Step;
    readonly busy: boolean;
    readonly showStartError: boolean;
    readonly strongerPaths: Scheme[];
    readonly onStepChange: (step: Step) => void;
    readonly onUpdate: (patch: SchemeUpdate) => void;
    readonly onStartErrorChange: (visible: boolean) => void;
    readonly onCancelNew?: () => void;
    readonly onRequestRemove: () => void;
    readonly onComplete: () => void;
}

function PathFlow(props: PathFlowProps) {
    const {
        scheme, tokenLabels, actionLabels, t, step, busy, showStartError, strongerPaths,
        onStepChange, onUpdate, onStartErrorChange, onCancelNew, onRequestRemove, onComplete,
    } = props;
    const startingOptions: TokenKind[] = scheme.action === "SIGN_IN" ? [...primaryTokens] : tokenKinds;
    const startingToken = scheme.requiredTokens.find(token => startingOptions.includes(token)) ??
        (scheme.action === "SIGN_IN" ? undefined : scheme.requiredTokens[0]);
    const extraTokens = scheme.requiredTokens.filter(token => token !== startingToken);
    const selectedFactor = extraTokens.find(token => factorTokens.includes(token as (typeof factorTokens)[number]));
    const hasStartingToken = startingToken !== undefined && startingOptions.includes(startingToken);
    const stepTitle = t(steps[step - 1]!);

    const chooseStartingToken = (value: string | undefined) => {
        if (!value || !startingOptions.includes(value as TokenKind)) return;
        const remaining = scheme.requiredTokens.filter(token => token !== startingToken && token !== value);
        onUpdate({ requiredTokens: [value as TokenKind, ...remaining] });
        onStartErrorChange(false);
    };

    const chooseSignInFactor = (value: string | undefined) => {
        if (!startingToken) return;
        onUpdate({ requiredTokens: value && factorTokens.includes(value as (typeof factorTokens)[number])
            ? [startingToken, value as TokenKind]
            : [startingToken] });
    };

    const toggleAccountProof = (token: TokenKind, checked: boolean) => {
        if (!startingToken) return;
        const remaining = extraTokens.filter(item => item !== token);
        onUpdate({ requiredTokens: [startingToken, ...(checked ? [...remaining, token] : remaining)] });
    };

    const continueToRank = () => {
        if (!hasStartingToken) {
            onStartErrorChange(true);
            return;
        }
        onStepChange(3);
    };

    return <Stack gap="stack">
        <Text variant="title2" as="h2">{ t("guidedEditorTitle") }</Text>
        <ol aria-label={ t("editorSteps") } className="flex flex-wrap gap-inline">
            { steps.map((name, index) => <li key={ name } aria-current={ step === index + 1 ? "step" : undefined }>
                <Text variant="small" color={ step === index + 1 ? "primary" : "muted" }>
                    { t("stepLabel", { number: index + 1, title: t(name) }) }
                </Text>
            </li>) }
        </ol>
        <Text variant="bodyStrong">{ t("stepLabel", { number: step, title: stepTitle }) }</Text>
        { step === 2 && <Stack gap="stack">
            <Text variant="body">{ t(scheme.action === "SIGN_IN" ? "signInProofHelp" : "accountProofHelp") }</Text>
            <Fieldset legend={ t(scheme.action === "SIGN_IN" ? "primaryMethod" : "startingProof") }>
                <RadioGroup
                    aria-label={ t(scheme.action === "SIGN_IN" ? "primaryMethod" : "startingProof") }
                    value={ startingToken ?? "" }
                    disabled={ busy }
                    onValueChange={ value => { chooseStartingToken(value); }}
                >
                    { startingOptions.map(token => <label key={ token }
                        className="flex cursor-pointer items-center gap-inline rounded-control border border-border-default p-stack"
                        onClick={ () => { if (!busy) chooseStartingToken(token); } }
                    >
                        <Radio value={ token } aria-label={ tokenLabels[token] } />
                        <Text variant="small">{ tokenLabels[token] }</Text>
                    </label>) }
                </RadioGroup>
                { showStartError && !hasStartingToken && <Text variant="small" role="alert" tone="danger">
                    { t("startingProofRequired") }
                </Text> }
            </Fieldset>
            { scheme.action === "SIGN_IN"
                ? <Fieldset legend={ t("additionalFactorOptional") }>
                    <RadioGroup
                        aria-label={ t("additionalFactorOptional") }
                        value={ selectedFactor ?? "none" }
                        disabled={ busy }
                        onValueChange={ value => { chooseSignInFactor(value === "none" ? undefined : value); }}
                    >
                        <label className="flex cursor-pointer items-center gap-inline rounded-control border border-border-default p-stack"
                            onClick={ () => { if (!busy) chooseSignInFactor(undefined); } }
                        >
                            <Radio value="none" aria-label={ t("noAdditionalProof") } />
                            <Text variant="small">{ t("noAdditionalProof") }</Text>
                        </label>
                        { factorTokens.map(token => <label key={ token }
                            className="flex cursor-pointer items-center gap-inline rounded-control border border-border-default p-stack"
                            onClick={ () => { if (!busy) chooseSignInFactor(token); } }
                        >
                            <Radio value={ token } aria-label={ tokenLabels[token] } />
                            <Text variant="small">{ tokenLabels[token] }</Text>
                        </label>) }
                    </RadioGroup>
                </Fieldset>
                : <Fieldset legend={ t("additionalProofsOptional") } disabled={ busy || !hasStartingToken }>
                    <Stack gap="inline">
                        { tokenKinds.filter(token => token !== startingToken).map(token => <Field key={ token } label={ tokenLabels[token] }>
                            <Checkbox checked={ extraTokens.includes(token) } disabled={ busy || !hasStartingToken }
                                onCheckedChange={ checked => { toggleAccountProof(token, checked); } } />
                        </Field>) }
                    </Stack>
                </Fieldset> }
            <Panel variant="inset">
                <Text variant="body">{ t("pathResult", { path: schemeTitle(scheme, tokenLabels) }) }</Text>
                <Text variant="small" color="secondary">{ t("proofsAnded") }</Text>
            </Panel>
            <div className="flex flex-wrap gap-inline">
                { onCancelNew && <Button tone="ghost" type="button" disabled={ busy } onClick={ onCancelNew}>{ t("cancelPath") }</Button> }
                <Button tone="primary" type="button" disabled={ busy } onClick={ continueToRank}>{ t("nextRank") }</Button>
            </div>
        </Stack> }
        { step === 3 && <Stack gap="stack">
            <Text variant="body">{ t("rankHelp") }</Text>
            <div className="flex flex-wrap gap-inline">
                { [1, 2, 3].map(rank => <Button key={ rank } tone={ scheme.assuranceRank === rank ? "primary" : "neutral" }
                    type="button" aria-pressed={ scheme.assuranceRank === rank } disabled={ busy }
                    onClick={ () => { onUpdate({ assuranceRank: rank }); }}>
                    { t("rankOption", { rank }) }
                </Button>) }
            </div>
            <Field label={ t("customRank") } description={ t("customRankHelp") }>
                <Input type="number" min={ 1 } step={ 1 }
                    value={ scheme.assuranceRank > 3 ? scheme.assuranceRank : "" }
                    placeholder={ t("customRankPlaceholder") }
                    disabled={ busy }
                    onChange={ event => { onUpdate({ assuranceRank: Number(event.target.value) }); }}
                />
            </Field>
            { (!Number.isInteger(scheme.assuranceRank) || scheme.assuranceRank < 1) && <Text variant="small" role="alert" tone="danger">
                { t("rankRequired") }
            </Text> }
            <Panel variant="inset"><Text variant="body">{ t("currentRank", { path: schemeTitle(scheme, tokenLabels), rank: scheme.assuranceRank }) }</Text></Panel>
            <div className="flex flex-wrap gap-inline">
                <Button tone="neutral" type="button" disabled={ busy } onClick={ () => { onStepChange(2); }}>{ t("backToProofs") }</Button>
                <Button tone="primary" type="button" disabled={ busy || !Number.isInteger(scheme.assuranceRank) || scheme.assuranceRank < 1 }
                    onClick={ () => { onStepChange(4); }}>
                    { t("nextReview") }
                </Button>
            </div>
        </Stack> }
        { step === 4 && <Stack gap="stack">
            <Text variant="body">{ t("reviewHelp") }</Text>
            <Panel variant="inset">
                <Text variant="bodyStrong">{ t("reviewSummary", {
                    action: actionLabels[scheme.action], path: schemeTitle(scheme, tokenLabels), rank: scheme.assuranceRank,
                }) }</Text>
                <Text variant="small" color="secondary">{ t("pathStatus", { state: t(scheme.enabled ? "policyEnabled" : "policyDisabled") }) }</Text>
                <Text variant="body" color="secondary">
                    { strongerPaths.length > 0 ? t("higherRankedPathNote") : t("highestRankedPathNote") }
                </Text>
                { strongerPaths.length > 0 && <ul className="list-disc pl-stack">
                    { strongerPaths.map(other => <li key={ other.id }>
                        <Text variant="small">{ t("rankedPathSummary", {
                            path: schemeTitle(other, tokenLabels), rank: other.assuranceRank,
                        }) }</Text>
                    </li>) }
                </ul> }
            </Panel>
            <div className="flex flex-wrap gap-inline">
                <Button tone="neutral" type="button" disabled={ busy } onClick={ () => { onStepChange(2); }}>{ t("editProofs") }</Button>
                <Button tone="danger" type="button" disabled={ busy } onClick={ onRequestRemove}>{ t("removePath") }</Button>
                <Button tone="primary" type="button" disabled={ busy } onClick={ onComplete}>{ t("doneEditingPath") }</Button>
            </div>
        </Stack> }
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
                : <div className="flex flex-col gap-stack">
                    { changes.map(change => <Panel key={ change.id } variant="inset">
                        <Text variant="small" color="muted">{ actionLabels[(change.draft ?? change.latest)!.action] }</Text>
                        <div className="grid grid-cols-1 gap-stack sm:grid-cols-2">
                            <Stack gap="inline-tight">
                                <Text variant="bodyStrong">{ t("yourDraft") }</Text>
                                <Text variant="body">{ change.draft ? schemeTitle(change.draft, tokenLabels) : t("pathRemoved") }</Text>
                                { change.draft && <Text variant="small" color="secondary">
                                    { t("rankAndState", { rank: change.draft.assuranceRank, state: change.draft.enabled ? t("policyEnabled") : t("policyDisabled") }) }
                                </Text> }
                            </Stack>
                            <Stack gap="inline-tight">
                                <Text variant="bodyStrong">{ t("latestPolicy") }</Text>
                                <Text variant="body">{ change.latest ? schemeTitle(change.latest, tokenLabels) : t("pathRemoved") }</Text>
                                { change.latest && <Text variant="small" color="secondary">
                                    { t("rankAndState", { rank: change.latest.assuranceRank, state: change.latest.enabled ? t("policyEnabled") : t("policyDisabled") }) }
                                </Text> }
                            </Stack>
                        </div>
                    </Panel>) }
                </div> }
            { draft.advancedAcknowledged !== latest.advancedAcknowledged && <Text variant="body" role="status">
                { t("emailRiskAckComparison", {
                    draft: t(draft.advancedAcknowledged ? "confirmed" : "notConfirmed"),
                    latest: t(latest.advancedAcknowledged ? "confirmed" : "notConfirmed"),
                }) }
            </Text> }
            <div className="flex flex-wrap gap-inline">
                <Button tone="neutral" type="button" onClick={ () => { onResolve("reload"); }}>
                    { t("discardDraftReload", { version: latest.version }) }
                </Button>
                <Button tone="danger" type="button" onClick={ () => { onResolve("replace"); }}>
                    { t("replaceLatestWithDraft", { version: latest.version }) }
                </Button>
            </div>
        </Stack>
    </Panel>;
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

function schemeTitle(scheme: Scheme, labels: TokenLabels): string {
    return tokenKinds.filter(token => scheme.requiredTokens.includes(token)).map(token => labels[token]).join(" + ");
}
