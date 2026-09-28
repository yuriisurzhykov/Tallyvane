import type { AddToastOptions } from "frontend-shared/ui/toast";
import type { useAdminAuthenticationStrings } from "@/app/i18n";
import { AdminAuthError, adminAuthClient } from "@/features/admin-login";
import type { Action, Policy, TokenKind } from "./AuthenticationPolicyEditor";

interface SchemeBody {
    id: string;
    action: Action;
    requiredTokens: TokenKind[];
    assuranceRank: number;
    enabled: boolean;
}

interface PolicyBody {
    version: number;
    schemes: SchemeBody[];
    advancedAcknowledged: boolean;
}

async function request<T>(path: string, method = "GET", body?: unknown): Promise<T> {
    return adminAuthClient.requestJson<T>(`/${path}`, method, body);
}

export async function requestPolicy(): Promise<Policy> {
    const body = await request<PolicyBody>("policy");
    return {
        version: body.version,
        schemes: body.schemes.map(scheme => ({
            id: scheme.id,
            action: scheme.action,
            requiredTokens: scheme.requiredTokens,
            assuranceRank: scheme.assuranceRank,
            enabled: scheme.enabled,
        })),
        advancedAcknowledged: body.advancedAcknowledged,
    };
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
    readonly setSavedPolicy?: (policy: Policy | null) => void;
    readonly setConflict?: (policy: Policy | null) => void;
    readonly setEmail?: (email: string) => void;
    readonly notify: (options: AddToastOptions) => void;
    readonly t: ReturnType<typeof useAdminAuthenticationStrings>;
    readonly fail: (reason: unknown) => void;
}

export async function savePolicy(context: PolicyMutationContext, acknowledged: boolean) {
    const { policy, invalid, setConfirmation, setBusy, setError, setValidationError, setPolicy, setSavedPolicy, setConflict, notify, t, fail } = context;
    if (!policy || invalid || !setValidationError || !setPolicy || !setSavedPolicy || !setConflict) return;
    setConfirmation(null);
    setBusy(true);
    setError(null);
    setValidationError(null);
    try {
        await request<unknown>("policy", "PUT", {
            schemes: policy.schemes.map(scheme => ({
                id: scheme.id,
                action: scheme.action,
                requiredTokens: scheme.requiredTokens,
                assuranceRank: scheme.assuranceRank,
                enabled: scheme.enabled,
            })),
            advancedAcknowledged: acknowledged,
            expectedVersion: policy.version,
        });
        const saved = await requestPolicy();
        setPolicy(saved);
        setSavedPolicy(saved);
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

export async function resetFactors(context: PolicyMutationContext) {
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
