"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { AppShell } from "frontend-shared/ui/app-shell";
import { Button } from "frontend-shared/ui/button";
import { Checkbox } from "frontend-shared/ui/checkbox";
import { Field } from "frontend-shared/ui/field";
import { Fieldset } from "frontend-shared/ui/fieldset";
import { Panel } from "frontend-shared/ui/panel";
import { Select } from "frontend-shared/ui/select";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import type { AddToastOptions } from "frontend-shared/ui/toast";
import { useToast } from "frontend-shared/ui/toast";
import { useAdminNavItems } from "@/app/navigation";
import { useAdminAuthenticationStrings } from "@/app/i18n";
import { ConfirmationDrawer, ResetFactorPanel } from "./AdminAuthenticationDialogs";

type Primary = "PASSWORD" | "GOOGLE" | "EMAIL_CODE";
type Requirement = "DISABLED" | "IF_ENROLLED" | "REQUIRED";
type Factor = "TOTP" | "EMAIL_OTP" | "BACKUP_CODE";

interface Rule {
    primary: Primary;
    enabled: boolean;
    requirement: Requirement;
    allowedMethods: Factor[]
}

interface Policy {
    version: number;
    rules: Rule[];
    advancedAcknowledged: boolean
}

const factors: Factor[] = ["TOTP", "EMAIL_OTP", "BACKUP_CODE"];
const requirements: Requirement[] = ["DISABLED", "IF_ENROLLED", "REQUIRED"];

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

export function AdminAuthenticationView() {
    return <Editor/>;
}

function Editor() {
    const t = useAdminAuthenticationStrings("adminAuthentication");
    const labels: Record<Primary | Factor | Requirement, string> = {
        PASSWORD: t("password"), GOOGLE: t("google"), EMAIL_CODE: t("emailCode"),
        TOTP: t("authenticator"), EMAIL_OTP: t("emailOtp"), BACKUP_CODE: t("backupCode"),
        DISABLED: t("disabled"), IF_ENROLLED: t("ifEnrolled"), REQUIRED: t("required"),
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
            setPolicy(await request<Policy>("policy"));
        } catch (reason) {
            fail(reason);
        } finally {
            setLoading(false);
        }
    }, [fail]);
    useEffect(() => {
        let active = true;
        void request<Policy>("policy").then(value => {
            if (active) setPolicy(value);
        }).catch((reason: unknown) => {
            if (active) fail(reason);
        }).finally(() => {
            if (active) setLoading(false);
        });
        return () => { active = false; };
    }, [fail]);
    const update = (primary: Primary, patch: Partial<Rule>) => { setPolicy(current => current && ({
        ...current, advancedAcknowledged: false,
        rules: current.rules.map(rule => rule.primary === primary ? { ...rule, ...patch } : rule),
    })); };
    const invalid = policy !== null && (!policy.rules.some(rule => rule.enabled) ||
        policy.rules.some(rule => rule.enabled && rule.requirement !== "DISABLED" && rule.allowedMethods.length === 0));
    const risky = policy?.rules.some(rule => rule.enabled && rule.primary !== "PASSWORD" &&
        rule.requirement !== "DISABLED" && rule.allowedMethods.includes("EMAIL_OTP"));

    const save = (acknowledged: boolean) => savePolicy({
        policy, invalid, setConfirmation, setBusy, setError, setPolicy,
        notify: options => { actions.add(options); }, t, fail,
    }, acknowledged);
    const reset = () => resetFactors({ email, setEmail, setConfirmation, setBusy, setError,
        notify: options => { actions.add(options); }, t, fail });
    return <EditorContent
        t={ t }
        labels={ labels }
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
        onEmailChange={ setEmail }
        onConfirmationChange={ setConfirmation }
        onSave={ save }
        onReset={ reset }
    />;
}

type Translate = ReturnType<typeof useAdminAuthenticationStrings>;
type Labels = Record<Primary | Factor | Requirement, string>;
type PolicyUpdate = (primary: Primary, patch: Partial<Rule>) => void;

interface EditorContentProps {
    readonly t: Translate;
    readonly labels: Labels;
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
    readonly onEmailChange: (email: string) => void;
    readonly onConfirmationChange: (value: "advanced" | "reset" | null) => void;
    readonly onSave: (acknowledged: boolean) => Promise<void>;
    readonly onReset: () => Promise<void>;
}

function EditorContent(props: EditorContentProps) {
    const { t, labels, policy, loading, busy, error, unauthorized, email, confirmation, invalid, risky } = props;
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
            { !loading && policy && <PolicyRules policy={ policy } labels={ labels } busy={ busy } t={ t } onUpdate={ props.onUpdate } /> }
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

interface PolicyRulesProps {
    readonly policy: Policy;
    readonly labels: Labels;
    readonly busy: boolean;
    readonly t: Translate;
    readonly onUpdate: PolicyUpdate;
}

function PolicyRules({ policy, labels, busy, t, onUpdate }: PolicyRulesProps) {
    return <Fieldset legend={ t("signInPolicy") } disabled={ busy }>
        { policy.rules.map(rule => <Panel key={ rule.primary } header={ <Text variant="bodyStrong">{ labels[rule.primary] }</Text> }>
            <Stack gap="stack">
                <Field label={ t("allowSignIn", { method: labels[rule.primary] }) }>
                    <Checkbox checked={ rule.enabled } disabled={ busy }
                              onCheckedChange={ enabled => { onUpdate(rule.primary, { enabled }); } } />
                </Field>
                <Select.Root value={ rule.requirement } disabled={ busy || !rule.enabled }
                             onValueChange={ value => { if (value) onUpdate(rule.primary, { requirement: value }); } }>
                    <Select.Label>{ t("secondFactorRequirement") }</Select.Label>
                    <Select.Trigger><Select.Value>{ labels[rule.requirement] }</Select.Value><Select.Icon /></Select.Trigger>
                    <Select.Popup>{ requirements.map(value => <Select.Item key={ value } value={ value }>{ labels[value] }</Select.Item>) }</Select.Popup>
                </Select.Root>
                <Fieldset legend={ t("allowedSecondFactors") }
                          disabled={ !rule.enabled || rule.requirement === "DISABLED" }
                          className="flex flex-row flex-wrap gap-stack">
                    { factors.map(factor => <Field key={ factor } label={ labels[factor] }>
                        <Checkbox checked={ rule.allowedMethods.includes(factor) }
                                  disabled={ busy || !rule.enabled || rule.requirement === "DISABLED" }
                                  onCheckedChange={ checked => {
                                      const allowedMethods = checked
                                          ? [...rule.allowedMethods, factor]
                                          : rule.allowedMethods.filter(item => item !== factor);
                                      onUpdate(rule.primary, { allowedMethods });
                                  }} />
                    </Field>) }
                </Fieldset>
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
            expectedVersion: policy.version,
            rules: policy.rules,
            advancedAcknowledged: acknowledged,
        });
        setPolicy(await request<Policy>("policy"));
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
