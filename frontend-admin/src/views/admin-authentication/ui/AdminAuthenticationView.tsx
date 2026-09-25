"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { AppShell } from "frontend-shared/ui/app-shell";
import { Button } from "frontend-shared/ui/button";
import { Checkbox } from "frontend-shared/ui/checkbox";
import { Field } from "frontend-shared/ui/field";
import { Fieldset } from "frontend-shared/ui/fieldset";
import { Input } from "frontend-shared/ui/input";
import { Panel } from "frontend-shared/ui/panel";
import { Select } from "frontend-shared/ui/select";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import type { AddToastOptions } from "frontend-shared/ui/toast";
import { useToast } from "frontend-shared/ui/toast";
import { useAdminNavItems } from "@/app/navigation";
import { useAdminAuthenticationStrings } from "@/app/i18n";
import { ConfirmationDrawer, ResetFactorPanel } from "./AdminAuthenticationDialogs";

type TokenKind = "PASSWORD" | "GOOGLE" | "EMAIL_SIGN_IN_CODE" | "TOTP" | "EMAIL_FACTOR_CODE" | "BACKUP_CODE";
type Action = "SIGN_IN" | "CHANGE_PRIMARY_CREDENTIAL" | "MANAGE_SECOND_FACTORS";

interface Scheme {
    id: string;
    action: Action;
    requiredTokens: TokenKind[];
    assuranceRank: number;
    enabled: boolean;
}

interface Policy {
    version: number;
    schemes: Scheme[];
    advancedAcknowledged: boolean;
}

interface SchemeBody {
    id: string;
    action: Action;
    required_tokens: TokenKind[];
    assurance_rank: number;
    enabled: boolean;
}

interface PolicyBody {
    version: number;
    schemes: SchemeBody[];
    advanced_acknowledged: boolean;
}

const tokenKinds: TokenKind[] = ["PASSWORD", "GOOGLE", "EMAIL_SIGN_IN_CODE", "TOTP", "EMAIL_FACTOR_CODE", "BACKUP_CODE"];
const actions: Action[] = ["SIGN_IN", "CHANGE_PRIMARY_CREDENTIAL", "MANAGE_SECOND_FACTORS"];
const primaryTokens = new Set<TokenKind>(["PASSWORD", "GOOGLE", "EMAIL_SIGN_IN_CODE"]);

class RequestError extends Error {
    public constructor(public readonly status: number) {
        super();
    }
}

function failureText(reason: unknown, t: ReturnType<typeof useAdminAuthenticationStrings>): string {
    if (!(reason instanceof RequestError)) return t("connectionFailed");
    if (reason.status === 401) return t("signInAdministrator");
    if (reason.status === 403) return t("administratorDenied");
    if (reason.status === 409) return t("stalePolicy");
    return t("requestFailed");
}

async function request<T>(path: string, method = "GET", body?: unknown): Promise<T> {
    const headers: Record<string, string> = { "Accept": "application/json" };
    if (method !== "GET") {
        const csrfResponse = await fetch("/api/v1/auth/csrf", { credentials: "same-origin", cache: "no-store" });
        if (!csrfResponse.ok) throw new RequestError(csrfResponse.status);
        const csrf = await csrfResponse.json() as { token: string };
        headers["X-CSRF-Token"] = csrf.token;
        headers["Content-Type"] = "application/json";
    }
    const response = await fetch(`/api/v1/auth/admin/${ path }`, {
        method, headers, credentials: "same-origin", cache: "no-store",
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
    if (!response.ok) throw new RequestError(response.status);
    return response.status === 204 ? undefined as T : await response.json() as T;
}

async function requestPolicy(): Promise<Policy> {
    const body = await request<PolicyBody>("policy");
    return {
        version: body.version,
        schemes: body.schemes.map(scheme => ({
            id: scheme.id,
            action: scheme.action,
            requiredTokens: scheme.required_tokens,
            assuranceRank: scheme.assurance_rank,
            enabled: scheme.enabled,
        })),
        advancedAcknowledged: body.advanced_acknowledged,
    };
}

export function AdminAuthenticationView() {
    return <Editor/>;
}

function Editor() {
    const t = useAdminAuthenticationStrings("adminAuthentication");
    const tokenLabels: Record<TokenKind, string> = {
        PASSWORD: t("password"), GOOGLE: t("google"), EMAIL_SIGN_IN_CODE: t("emailCode"),
        TOTP: t("authenticator"), EMAIL_FACTOR_CODE: t("emailOtp"), BACKUP_CODE: t("backupCode"),
    };
    const actionLabels: Record<Action, string> = {
        SIGN_IN: t("signIn"), CHANGE_PRIMARY_CREDENTIAL: t("changePrimaryCredential"),
        MANAGE_SECOND_FACTORS: t("manageSecondFactors"),
    };
    const [policy, setPolicy] = useState<Policy | null>(null);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [unauthorized, setUnauthorized] = useState(false);
    const [email, setEmail] = useState("");
    const [confirmation, setConfirmation] = useState<"advanced" | "reset" | null>(null);
    const { actions } = useToast();
    const fail = useCallback((reason: unknown) => {
        const message = failureText(reason, t);
        setError(message);
        actions.add({ title: t("settingsLoadFailed"), description: message, tone: "danger" });
        setUnauthorized(reason instanceof RequestError && reason.status === 401);
    }, [actions, t]);
    const load = useCallback(async () => {
        setLoading(true);
        setError(null);
        setUnauthorized(false);
        try {
            setPolicy(await requestPolicy());
        } catch (reason) {
            fail(reason);
        } finally {
            setLoading(false);
        }
    }, [fail]);
    useEffect(() => {
        let active = true;
        void requestPolicy().then(value => {
            if (active) setPolicy(value);
        }).catch((reason: unknown) => {
            if (active) fail(reason);
        }).finally(() => {
            if (active) setLoading(false);
        });
        return () => { active = false; };
    }, [fail]);
    const update = (id: string, patch: Partial<Scheme>) => { setPolicy(current => current && ({
        ...current, advancedAcknowledged: false,
        schemes: current.schemes.map(scheme => scheme.id === id ? { ...scheme, ...patch } : scheme),
    })); };
    const invalid = policy !== null && (!policy.schemes.some(scheme => scheme.enabled && scheme.action === "SIGN_IN") ||
        policy.schemes.some(scheme => scheme.enabled && (scheme.requiredTokens.length === 0 || scheme.assuranceRank < 1 ||
            (scheme.action === "SIGN_IN" && scheme.requiredTokens.filter(token => primaryTokens.has(token)).length !== 1))) ||
        [...new Set(policy.schemes.filter(scheme => scheme.enabled && scheme.action === "SIGN_IN")
            .flatMap(scheme => scheme.requiredTokens.filter(token => primaryTokens.has(token))))]
            .some(primary => !policy.schemes.some(scheme => scheme.enabled && scheme.action === "SIGN_IN" &&
                scheme.requiredTokens.length === 1 && scheme.requiredTokens[0] === primary)));
    const risky = policy?.schemes.some(scheme => scheme.enabled && scheme.requiredTokens.includes("EMAIL_SIGN_IN_CODE") &&
        scheme.requiredTokens.includes("EMAIL_FACTOR_CODE"));

    const save = (acknowledged: boolean) => savePolicy({
        policy, invalid, setConfirmation, setBusy, setError, setPolicy,
        notify: options => { actions.add(options); }, t, fail,
    }, acknowledged);
    const reset = () => resetFactors({ email, setEmail, setConfirmation, setBusy, setError,
        notify: options => { actions.add(options); }, t, fail });
    return <EditorContent
        t={ t }
        tokenLabels={ tokenLabels }
        actionLabels={ actionLabels }
        policy={ policy }
        loading={ loading }
        busy={ busy }
        error={ error }
        unauthorized={ unauthorized }
        email={ email }
        confirmation={ confirmation }
        invalid={ invalid }
        risky={ risky }
        onReload={ load }
        onUpdate={ update }
        onAdd={ () => setPolicy(current => current && ({
            ...current,
            advancedAcknowledged: false,
            schemes: [...current.schemes, {
                id: globalThis.crypto.randomUUID(), action: "SIGN_IN", requiredTokens: ["PASSWORD"],
                assuranceRank: 1, enabled: true,
            }],
        })) }
        onRemove={ id => setPolicy(current => current && ({
            ...current, advancedAcknowledged: false,
            schemes: current.schemes.filter(scheme => scheme.id !== id),
        })) }
        onEmailChange={ setEmail }
        onConfirmationChange={ setConfirmation }
        onSave={ save }
        onReset={ reset }
    />;
}

type Translate = ReturnType<typeof useAdminAuthenticationStrings>;
type TokenLabels = Record<TokenKind, string>;
type ActionLabels = Record<Action, string>;
type PolicyUpdate = (id: string, patch: Partial<Scheme>) => void;

interface EditorContentProps {
    readonly t: Translate;
    readonly tokenLabels: TokenLabels;
    readonly actionLabels: ActionLabels;
    readonly policy: Policy | null;
    readonly loading: boolean;
    readonly busy: boolean;
    readonly error: string | null;
    readonly unauthorized: boolean;
    readonly email: string;
    readonly confirmation: "advanced" | "reset" | null;
    readonly invalid: boolean;
    readonly risky: boolean | undefined;
    readonly onReload: () => Promise<void>;
    readonly onUpdate: PolicyUpdate;
    readonly onAdd: () => void;
    readonly onRemove: (id: string) => void;
    readonly onEmailChange: (email: string) => void;
    readonly onConfirmationChange: (value: "advanced" | "reset" | null) => void;
    readonly onSave: (acknowledged: boolean) => Promise<void>;
    readonly onReset: () => Promise<void>;
}

function EditorContent(props: EditorContentProps) {
    const { t, tokenLabels, actionLabels, policy, loading, busy, error, unauthorized, email, confirmation, invalid, risky } = props;
    return <AppShell navItems={ useAdminNavItems("/authentication") } title={ t("title") } skipLinkLabel={ t("skipLink") }>
        <Stack gap="stack">
            <Text variant="body">{ t("description") }</Text>
            { error && <Stack role="alert" gap="inline">
                <Text variant="body" tone="danger">{ error }</Text>
                { unauthorized && <Link href="/login?returnTo=/authentication">{ t("signInAdminLink") }</Link> }
                <Button tone="neutral" disabled={ busy || loading } onClick={ () => { void props.onReload(); } }>
                    { t("reloadPolicy") }
                </Button>
            </Stack> }
            { loading && <Text variant="body" role="status">{ t("loading") }</Text> }
            { !loading && policy && <PolicySchemes policy={ policy } tokenLabels={ tokenLabels } actionLabels={ actionLabels }
                busy={ busy } t={ t } onUpdate={ props.onUpdate } onRemove={ props.onRemove } /> }
            { !loading && policy && <Button tone="neutral" disabled={ busy } onClick={ props.onAdd }>{ t("addScheme") }</Button> }
            { invalid && <Text variant="body" role="alert" tone="danger">{ t("enablePolicyError") }</Text> }
            { policy && <Button tone="primary" loading={ busy } disabled={ invalid }
                                onClick={ () => { if (risky) props.onConfirmationChange("advanced"); else void props.onSave(false); } }>
                { t("savePolicy") }
            </Button> }
            { !loading && policy && <ResetFactorPanel t={ t } email={ email } busy={ busy }
                                                      onEmailChange={ props.onEmailChange }
                                                      onRequest={() => { props.onConfirmationChange("reset"); }} /> }
        </Stack>
        <ConfirmationDrawer t={ t } email={ email } confirmation={ confirmation }
                            onClose={() => { props.onConfirmationChange(null); }}
                            onConfirm={() => {
                                if (confirmation === "advanced") void props.onSave(true);
                                else void props.onReset();
                            }} />
    </AppShell>;
}

interface PolicySchemesProps {
    readonly policy: Policy;
    readonly tokenLabels: TokenLabels;
    readonly actionLabels: ActionLabels;
    readonly busy: boolean;
    readonly t: Translate;
    readonly onUpdate: PolicyUpdate;
    readonly onRemove: (id: string) => void;
}

function PolicySchemes({ policy, tokenLabels, actionLabels, busy, t, onUpdate, onRemove }: PolicySchemesProps) {
    return <Fieldset legend={ t("signInPolicy") } disabled={ busy }>
        { policy.schemes.map(scheme => <Panel key={ scheme.id } header={ <Text variant="bodyStrong">{ actionLabels[scheme.action] } · { scheme.assuranceRank }</Text> }>
            <Stack gap="stack">
                <Field label={ t("schemeEnabled") }>
                    <Checkbox checked={ scheme.enabled } disabled={ busy }
                              onCheckedChange={ enabled => { onUpdate(scheme.id, { enabled }); } } />
                </Field>
                <Select.Root value={ scheme.action } disabled={ busy || !scheme.enabled }
                             onValueChange={ value => {
                                 if (value && actions.includes(value as Action)) onUpdate(scheme.id, { action: value as Action });
                             } }>
                    <Select.Label>{ t("action") }</Select.Label>
                    <Select.Trigger><Select.Value>{ actionLabels[scheme.action] }</Select.Value><Select.Icon /></Select.Trigger>
                    <Select.Popup>{ actions.map(value => <Select.Item key={ value } value={ value }>{ actionLabels[value] }</Select.Item>) }</Select.Popup>
                </Select.Root>
                <Fieldset legend={ t("requiredTokens") } disabled={ !scheme.enabled }
                          className="flex flex-row flex-wrap gap-stack">
                    { tokenKinds.map(token => <Field key={ token } label={ tokenLabels[token] }>
                        <Checkbox checked={ scheme.requiredTokens.includes(token) }
                                  disabled={ busy || !scheme.enabled }
                                  onCheckedChange={ checked => {
                                      const requiredTokens = checked
                                          ? [...scheme.requiredTokens, token]
                                          : scheme.requiredTokens.filter(item => item !== token);
                                      onUpdate(scheme.id, { requiredTokens });
                                  }} />
                    </Field>) }
                </Fieldset>
                <Field label={ t("assuranceRank") }>
                    <Input type="number" min={ 1 } step={ 1 } required value={ scheme.assuranceRank }
                           disabled={ busy || !scheme.enabled }
                           onChange={ event => { onUpdate(scheme.id, { assuranceRank: Number(event.target.value) }); } } />
                </Field>
                <Button tone="danger" type="button" disabled={ busy } onClick={ () => { onRemove(scheme.id); } }>
                    { t("removeScheme") }
                </Button>
            </Stack>
        </Panel>) }
    </Fieldset>;
}

interface PolicyMutationContext {
    readonly policy?: Policy | null;
    readonly invalid?: boolean;
    readonly email?: string;
    readonly setConfirmation: (value: "advanced" | "reset" | null) => void;
    readonly setBusy: (busy: boolean) => void;
    readonly setError: (error: string | null) => void;
    readonly setPolicy?: (policy: Policy) => void;
    readonly setEmail?: (email: string) => void;
    readonly notify: (options: AddToastOptions) => void;
    readonly t: ReturnType<typeof useAdminAuthenticationStrings>;
    readonly fail: (reason: unknown) => void;
}

async function savePolicy(context: PolicyMutationContext, acknowledged: boolean) {
    const { policy, invalid, setConfirmation, setBusy, setError, setPolicy, notify, t, fail } = context;
    if (!policy || invalid || !setPolicy) return;
    setConfirmation(null);
    setBusy(true);
    setError(null);
    try {
        await request<unknown>("policy", "PUT", {
            schemes: policy.schemes.map(scheme => ({
                id: scheme.id,
                action: scheme.action,
                required_tokens: scheme.requiredTokens,
                assurance_rank: scheme.assuranceRank,
                enabled: scheme.enabled,
            })),
            advanced_acknowledged: acknowledged,
            expected_version: policy.version,
        });
        setPolicy(await requestPolicy());
        notify({ title: t("policySaved"), tone: "success" });
    } catch (reason: unknown) {
        fail(reason);
    } finally {
        setBusy(false);
    }
}

async function resetFactors(context: PolicyMutationContext) {
    const { email, setEmail, setConfirmation, setBusy, setError, notify, t, fail } = context;
    if (email === undefined || !setEmail) return;
    setConfirmation(null);
    setBusy(true);
    setError(null);
    try {
        await request<unknown>("mfa/reset", "POST", { email: email.trim(), confirmation: true });
        notify({
            title: t("factorsReset"),
            description: t("factorsResetDescription"),
            tone: "success",
        });
        setEmail("");
    } catch (reason: unknown) {
        fail(reason);
    } finally {
        setBusy(false);
    }
}
