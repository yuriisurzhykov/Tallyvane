"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useToast } from "frontend-shared/ui/toast";
import { useAdminAuthenticationStrings } from "@/app/i18n";
import { EditorContent } from "./AdminAuthenticationContent";
import type { Action, Policy, Scheme, TokenKind } from "./AuthenticationPolicyEditor";
import { AdminAuthError } from "@/features/admin-login";
import { requestPolicy, savePolicy, resetFactors } from "./AdminAuthenticationMutations";
import { SettingsNavigationGuardProvider } from "settings-kit/features/settings-navigation";

const primaryTokens = new Set<TokenKind>(["PASSWORD", "GOOGLE", "EMAIL_SIGN_IN_CODE"]);



function failureText(reason: unknown, t: ReturnType<typeof useAdminAuthenticationStrings>): string {
    if (!(reason instanceof AdminAuthError)) return t("connectionFailed");
    if (reason.status === 401) return t("signInAdministrator");
    if (reason.status === 403) return t("administratorDenied");
    if (reason.status === 409) return t("stalePolicy");
    if (reason.status === 422) return reason.problem?.detail ?? t("serverPolicyInvalid");
    return t("requestFailed");
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
    return null;
}

function hasPolicyDraftChanges(current: Policy, saved: Policy): boolean {
    if (current.advancedAcknowledged !== saved.advancedAcknowledged || current.schemes.length !== saved.schemes.length) {
        return true;
    }
    return current.schemes.some((scheme, index) => {
        const original = saved.schemes[index];
        if (!original) return true;
        return scheme.id !== original.id || scheme.action !== original.action ||
            scheme.assuranceRank !== original.assuranceRank || scheme.enabled !== original.enabled ||
            scheme.requiredTokens.length !== original.requiredTokens.length ||
            scheme.requiredTokens.some((token, tokenIndex) => token !== original.requiredTokens[tokenIndex]);
    });
}

export function AdminAuthenticationView({ section = "policy" }: { readonly section?: "policy" | "accounts" }) {
    const t = useAdminAuthenticationStrings("adminAuthentication");
    const router = useRouter();
    return <SettingsNavigationGuardProvider
        labels={{
            title: t("unsavedSettingsTitle"),
            description: t("unsavedSettingsDescription"),
            stay: t("stayOnSettings"),
            leave: t("leaveSettings"),
        }}
        navigate={href => { router.push(href); }}
    >
        <Editor section={section} />
    </SettingsNavigationGuardProvider>;
}

function usePolicyState(t: ReturnType<typeof useAdminAuthenticationStrings>) {
    const [policy, setPolicy] = useState<Policy | null>(null);
    const [savedPolicy, setSavedPolicy] = useState<Policy | null>(null);
    const [editorResetKey, setEditorResetKey] = useState(0);
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
    const policyDirty = policy !== null && savedPolicy !== null && hasPolicyDraftChanges(policy, savedPolicy);
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
            const loaded = await requestPolicy();
            setPolicy(loaded);
            setSavedPolicy(loaded);
        } catch (reason) {
            fail(reason);
        } finally {
            setLoading(false);
        }
    }, [fail]);
    useEffect(() => {
        let active = true;
        void requestPolicy().then(value => {
            if (active) { setPolicy(value); setSavedPolicy(value); }
        }).catch((reason: unknown) => {
            if (active) fail(reason);
        }).finally(() => {
            if (active) setLoading(false);
        });
        return () => { active = false; };
    }, [fail]);

    return { policy, savedPolicy, editorResetKey, conflict, loading, busy, error, validationError, unauthorized,
        email, confirmation, schemeToRemove, actions, policyDirty, fail, load, setPolicy, setSavedPolicy,
        setEditorResetKey, setConflict, setBusy, setError, setValidationError, setEmail, setConfirmation,
        setSchemeToRemove };
}

function useDraftActions(state: ReturnType<typeof usePolicyState>, t: ReturnType<typeof useAdminAuthenticationStrings>) {
    const { policy, savedPolicy, conflict, email, actions, fail, setPolicy, setSavedPolicy, setEditorResetKey,
        setConflict, setBusy, setError, setValidationError, setEmail, setConfirmation, setSchemeToRemove } = state;
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
        setPolicy, setSavedPolicy, setConflict, notify: options => { actions.add(options); }, t, fail,
    }, acknowledged);
    const reset = () => resetFactors({ email, setEmail, setConfirmation, setBusy, setError,
        notify: options => { actions.add(options); }, t, fail });

    const add = (scheme: Omit<Scheme, "id">) => {
        const id = globalThis.crypto.randomUUID();
        setError(null);
        setValidationError(null);
        setPolicy(current => current && ({
            ...current,
            advancedAcknowledged: false,
            schemes: [...current.schemes, { ...scheme, id }],
        }));
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
        setSavedPolicy(conflict);
        setConflict(null);
    };

    const cancelDraft = () => {
        const restored = conflict ?? savedPolicy;
        if (!restored) return;
        setPolicy(restored);
        setSavedPolicy(restored);
        setConflict(null);
        setError(null);
        setValidationError(null);
        setEditorResetKey(current => current + 1);
    };

    return { update, issue, risky, save, reset, add, remove, resolveConflict, cancelDraft };
}

function Editor({ section }: { readonly section: "policy" | "accounts" }) {
    const t = useAdminAuthenticationStrings("adminAuthentication");
    const tokenLabels: Record<TokenKind, string> = {
        PASSWORD: t("password"), GOOGLE: t("google"), EMAIL_SIGN_IN_CODE: t("emailCode"),
        TOTP: t("authenticator"), EMAIL_FACTOR_CODE: t("emailOtp"),
    };
    const actionLabels: Record<Action, string> = {
        SIGN_IN: t("signIn"), CHANGE_PRIMARY_CREDENTIAL: t("changePrimaryCredential"),
        MANAGE_SECOND_FACTORS: t("manageSecondFactors"),
    };
    const state = usePolicyState(t);
    const { policy, editorResetKey, conflict, loading, busy, error, validationError, unauthorized, email,
        confirmation, schemeToRemove, policyDirty, load, setEmail, setConfirmation, setSchemeToRemove } = state;
    const { update, issue, risky, save, reset, add, remove, resolveConflict, cancelDraft } = useDraftActions(state, t);

    return <EditorContent
        section={section}
        t={ t }
        tokenLabels={ tokenLabels }
        actionLabels={ actionLabels }
        policy={ policy }
        policyDirty={ policyDirty }
        editorResetKey={ editorResetKey }
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
        onRequestRemove={ scheme => { setSchemeToRemove(scheme); setConfirmation("remove"); } }
        onEmailChange={ setEmail }
        onConfirmationChange={ value => {
            setConfirmation(value);
            if (value !== "remove") setSchemeToRemove(null);
        } }
        onResolveConflict={ resolveConflict }
        onCancelDraft={ cancelDraft }
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


