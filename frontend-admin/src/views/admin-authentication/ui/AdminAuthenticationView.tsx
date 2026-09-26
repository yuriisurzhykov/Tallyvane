"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { AppShell } from "frontend-shared/ui/app-shell";
import { Button } from "frontend-shared/ui/button";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import type { AddToastOptions } from "frontend-shared/ui/toast";
import { useToast } from "frontend-shared/ui/toast";
import { useAdminNavItems } from "@/app/navigation";
import { useAdminAuthenticationStrings } from "@/app/i18n";
import { AdminAuthError, adminAuthClient } from "@/features/admin-login";
import { AuthenticationPolicyEditor, type Action, type Policy, type Scheme, type TokenKind } from "./AuthenticationPolicyEditor";
import { ConfirmationDrawer, ResetFactorPanel } from "./AdminAuthenticationDialogs";

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

const primaryTokens = new Set<TokenKind>(["PASSWORD", "GOOGLE", "EMAIL_SIGN_IN_CODE"]);



function failureText(reason: unknown, t: ReturnType<typeof useAdminAuthenticationStrings>): string {
    if (!(reason instanceof AdminAuthError)) return t("connectionFailed");
    if (reason.status === 401) return t("signInAdministrator");
    if (reason.status === 403) return t("administratorDenied");
    if (reason.status === 409) return t("stalePolicy");
    if (reason.status === 422) return reason.problem?.detail ?? t("serverPolicyInvalid");
    return t("requestFailed");
}

async function request<T>(path: string, method = "GET", body?: unknown): Promise<T> {
    return adminAuthClient.requestJson<T>(`/admin/${path}`, method, body);
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

function policyIssue(policy: Policy, t: ReturnType<typeof useAdminAuthenticationStrings>): string | null {
    const signInSchemes = policy.schemes.filter(scheme => scheme.action === "SIGN_IN" && scheme.enabled);
    if (signInSchemes.length === 0) return t("noSignInPathError");
    if (policy.schemes.length === 0 || policy.schemes.some(scheme => scheme.requiredTokens.length === 0)) {
        return t("incompletePathError");
    }
    if (policy.schemes.some(scheme => !Number.isInteger(scheme.assuranceRank) || scheme.assuranceRank < 1)) {
        return t("invalidRankError");
    }
    if (policy.schemes.some(scheme => scheme.action === "SIGN_IN" &&
        (scheme.requiredTokens.filter(token => primaryTokens.has(token)).length !== 1 ||
            scheme.requiredTokens.filter(token => !primaryTokens.has(token)).length > 1))) {
        return t("signInCompositionError");
    }
    const enabledPrimaries = new Set(signInSchemes.flatMap(scheme => scheme.requiredTokens.filter(token => primaryTokens.has(token))));
    const missingFallback = [...enabledPrimaries].some(primary => !signInSchemes.some(scheme =>
        scheme.requiredTokens.length === 1 && scheme.requiredTokens[0] === primary,
    ));
    if (missingFallback) return t("primaryFallbackError");
    return null;
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
    const [conflict, setConflict] = useState<Policy | null>(null);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [validationError, setValidationError] = useState<string | null>(null);
    const [unauthorized, setUnauthorized] = useState(false);
    const [email, setEmail] = useState("");
    const [confirmation, setConfirmation] = useState<"advanced" | "reset" | "remove" | null>(null);
    const [schemeToRemove, setSchemeToRemove] = useState<Scheme | null>(null);
    const { actions } = useToast();
    const fail = useCallback((reason: unknown) => {
        const message = failureText(reason, t);
        setError(message);
        setValidationError(reason instanceof AdminAuthError && reason.status === 422 ? message : null);
        actions.add({ title: t("settingsLoadFailed"), description: message, tone: "danger" });
        setUnauthorized(reason instanceof AdminAuthError && reason.status === 401);
    }, [actions, t]);
    const load = useCallback(async () => {
        setLoading(true);
        setError(null);
        setValidationError(null);
        setConflict(null);
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

    const update = (id: string, patch: Partial<Scheme>) => {
        setError(null);
        setValidationError(null);
        setPolicy(current => current && ({
            ...current,
            advancedAcknowledged: false,
            schemes: current.schemes.map(scheme => scheme.id === id ? { ...scheme, ...patch } : scheme),
        }));
    };

    const issue = policy ? policyIssue(policy, t) : null;
    const risky = policy?.schemes.some(scheme => scheme.requiredTokens.includes("EMAIL_SIGN_IN_CODE") &&
        scheme.requiredTokens.includes("EMAIL_FACTOR_CODE"));

    const save = (acknowledged: boolean) => savePolicy({
        policy, invalid: issue !== null, setConfirmation, setBusy, setError, setValidationError,
        setPolicy, setConflict, notify: options => { actions.add(options); }, t, fail,
    }, acknowledged);
    const reset = () => resetFactors({ email, setEmail, setConfirmation, setBusy, setError,
        notify: options => { actions.add(options); }, t, fail });

    const add = (action: Action): string => {
        const id = globalThis.crypto.randomUUID();
        setError(null);
        setValidationError(null);
        setPolicy(current => current && ({
            ...current,
            advancedAcknowledged: false,
            schemes: [...current.schemes, { id, action, requiredTokens: [], assuranceRank: 1, enabled: true }],
        }));
        return id;
    };
    const remove = (id: string) => {
        setError(null);
        setValidationError(null);
        setConfirmation(null);
        setSchemeToRemove(null);
        setPolicy(current => current && ({
            ...current,
            advancedAcknowledged: false,
            schemes: current.schemes.filter(scheme => scheme.id !== id),
        }));
    };

    const resolveConflict = (resolution: "reload" | "replace") => {
        if (!conflict) return;
        setError(null);
        setValidationError(null);
        if (resolution === "reload") {
            setPolicy(conflict);
        } else {
            setPolicy(current => current && ({ ...current, version: conflict.version }));
        }
        setConflict(null);
    };

    return <EditorContent
        t={ t }
        tokenLabels={ tokenLabels }
        actionLabels={ actionLabels }
        policy={ policy }
        conflict={ conflict }
        loading={ loading }
        busy={ busy }
        error={ error }
        validationError={ validationError }
        unauthorized={ unauthorized }
        email={ email }
        confirmation={ confirmation }
        schemeToRemove={ schemeToRemove }
        policyIssue={ issue }
        onReload={ load }
        onUpdate={ update }
        onAdd={ add }
        onRemove={ remove }
        onRequestRemove={ scheme => { setSchemeToRemove(scheme); setConfirmation("remove"); } }
        onEmailChange={ setEmail }
        onConfirmationChange={ value => {
            setConfirmation(value);
            if (value !== "remove") setSchemeToRemove(null);
        } }
        onResolveConflict={ resolveConflict }
        onSave={ () => {
            if (risky) setConfirmation("advanced");
            else void save(false);
        } }
        onReset={ reset }
        onConfirm={ () => {
            if (confirmation === "advanced") void save(true);
            else if (confirmation === "remove" && schemeToRemove) remove(schemeToRemove.id);
            else void reset();
        }}
    />;
}

type Translate = ReturnType<typeof useAdminAuthenticationStrings>;
type RemoveConfirmation = Pick<Scheme, "action" | "requiredTokens" | "assuranceRank">;

interface EditorContentProps {
    readonly t: Translate;
    readonly tokenLabels: Record<TokenKind, string>;
    readonly actionLabels: Record<Action, string>;
    readonly policy: Policy | null;
    readonly conflict: Policy | null;
    readonly loading: boolean;
    readonly busy: boolean;
    readonly error: string | null;
    readonly validationError: string | null;
    readonly unauthorized: boolean;
    readonly email: string;
    readonly confirmation: "advanced" | "reset" | "remove" | null;
    readonly schemeToRemove: RemoveConfirmation | null;
    readonly policyIssue: string | null;
    readonly onReload: () => Promise<void>;
    readonly onUpdate: (id: string, patch: Partial<Scheme>) => void;
    readonly onAdd: (action: Action) => string;
    readonly onRemove: (id: string) => void;
    readonly onRequestRemove: (scheme: Scheme) => void;
    readonly onEmailChange: (email: string) => void;
    readonly onConfirmationChange: (value: "advanced" | "reset" | "remove" | null) => void;
    readonly onResolveConflict: (resolution: "reload" | "replace") => void;
    readonly onSave: () => void;
    readonly onReset: () => Promise<void>;
    readonly onConfirm: () => void;
}

function EditorContent(props: EditorContentProps) {
    const {
        t, tokenLabels, actionLabels, policy, conflict, loading, busy, error, validationError, unauthorized,
        email, confirmation, schemeToRemove, policyIssue,
    } = props;
    const removal = schemeToRemove ? {
        action: actionLabels[schemeToRemove.action],
        path: schemeToRemove.requiredTokens.map(token => tokenLabels[token]).join(" + "),
        rank: schemeToRemove.assuranceRank,
    } : undefined;

    return <AppShell navItems={ useAdminNavItems("/authentication") } title={ t("title") } skipLinkLabel={ t("skipLink") }>
        <Stack gap="stack">
            <Text variant="body">{ t("description") }</Text>
            { error && <Stack role="alert" gap="inline">
                <Text variant="body" tone="danger">{ error }</Text>
                { unauthorized && <Link href="/login?returnTo=/authentication">{ t("signInAdminLink") }</Link> }
                { !conflict && <Button tone="neutral" disabled={ busy || loading } onClick={ () => { void props.onReload(); } }>
                    { t("reloadPolicy") }
                </Button> }
            </Stack> }
            { loading && <Text variant="body" role="status">{ t("loading") }</Text> }
            { !loading && policy && <AuthenticationPolicyEditor
                key={ policy.version }
                t={ t }
                tokenLabels={ tokenLabels }
                actionLabels={ actionLabels }
                policy={ policy }
                busy={ busy }
                policyIssue={ policyIssue }
                validationError={ validationError }
                conflict={ conflict }
                onUpdate={ props.onUpdate }
                onAdd={ props.onAdd }
                onRemove={ props.onRemove }
                onRequestRemove={ props.onRequestRemove }
                onSave={ props.onSave }
                onResolveConflict={ props.onResolveConflict }
            /> }
            { !loading && policy && <ResetFactorPanel t={ t } email={ email } busy={ busy }
                onEmailChange={ props.onEmailChange }
                onRequest={ () => { props.onConfirmationChange("reset"); }} /> }
        </Stack>
        <ConfirmationDrawer t={ t } email={ email } confirmation={ confirmation } { ...(removal ? { removal } : {}) }
            onClose={ () => { props.onConfirmationChange(null); }}
            onConfirm={ props.onConfirm }
        />
    </AppShell>;
}

interface PolicyMutationContext {
    readonly policy?: Policy | null;
    readonly invalid?: boolean;
    readonly email?: string;
    readonly setConfirmation: (value: "advanced" | "reset" | "remove" | null) => void;
    readonly setBusy: (busy: boolean) => void;
    readonly setError: (error: string | null) => void;
    readonly setValidationError?: (error: string | null) => void;
    readonly setPolicy?: (policy: Policy | null | ((current: Policy | null) => Policy | null)) => void;
    readonly setConflict?: (policy: Policy | null) => void;
    readonly setEmail?: (email: string) => void;
    readonly notify: (options: AddToastOptions) => void;
    readonly t: ReturnType<typeof useAdminAuthenticationStrings>;
    readonly fail: (reason: unknown) => void;
}

async function savePolicy(context: PolicyMutationContext, acknowledged: boolean) {
    const { policy, invalid, setConfirmation, setBusy, setError, setValidationError, setPolicy, setConflict, notify, t, fail } = context;
    if (!policy || invalid || !setValidationError || !setPolicy || !setConflict) return;
    setConfirmation(null);
    setBusy(true);
    setError(null);
    setValidationError(null);
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
        setConflict(null);
        notify({ title: t("policySaved"), tone: "success" });
    } catch (reason: unknown) {
        if (reason instanceof AdminAuthError && reason.status === 409) {
            fail(reason);
            try {
                setConflict(await requestPolicy());
            } catch (loadReason: unknown) {
                fail(loadReason);
            }
        } else {
            fail(reason);
        }
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
