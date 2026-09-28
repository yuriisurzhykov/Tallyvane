"use client";

import { useCallback, useEffect, useState, type Dispatch, type SetStateAction } from "react";
import {
    issueActionProof, readActionSchemes, requestActionEmailCode,
    type AccountAction, type ActionSchemeOption, type PresentedProofToken, type ProofTokenKind,
} from "../../../features/authentication/api/actionProof";
import { authClient } from "../../../features/authentication/api/client";

interface GoogleProof { value: string; codeVerifier: string; redirectUri: string }
type GoogleProofMessage = { type: "tallyvane-google-action-proof"; state: string; error: boolean } & GoogleProof;
const EMAIL_KINDS = ["EMAIL_SIGN_IN_CODE", "EMAIL_FACTOR_CODE"] as const;
type ProofError = "load" | "required" | "invalid" | "google" | null;

function useAvailableSchemes(action: AccountAction, setError: Dispatch<SetStateAction<ProofError>>) {
    const [schemes, setSchemes] = useState<ActionSchemeOption[]>([]);
    const [schemeId, setSchemeId] = useState("");
    const [loading, setLoading] = useState(true);
    const refresh = useCallback(async () => {
        try {
            const available = await readActionSchemes(action);
            setSchemes(available);
            setSchemeId(current => available.some(scheme => scheme.id === current) ? current : available[0]?.id ?? "");
        } catch {
            setSchemes([]);
            setSchemeId("");
            setError("load");
        } finally {
            setLoading(false);
        }
    }, [action, setError]);
    useEffect(() => {
        const scheduled = window.setTimeout(() => { void refresh(); }, 0);
        return () => { window.clearTimeout(scheduled); };
    }, [refresh]);
    return { schemes, scheme: schemes.find(option => option.id === schemeId) ?? null, setSchemeId, refresh, loading };
}

function isGoogleProofMessage(value: unknown): value is GoogleProofMessage {
    if (!value || typeof value !== "object") return false;
    const message = value as Record<string, unknown>;
    return message.type === "tallyvane-google-action-proof" && typeof message.state === "string" &&
        typeof message.error === "boolean" && typeof message.value === "string" &&
        typeof message.codeVerifier === "string" && typeof message.redirectUri === "string";
}

function awaitGoogleProof(popup: Window, expectedState: string): Promise<GoogleProof> {
    return new Promise((resolve, reject) => {
        function cleanup() {
            window.clearTimeout(timeout);
            window.clearInterval(closedCheck);
            window.removeEventListener("message", receive);
        }
        const timeout = window.setTimeout(() => {
            cleanup();
            reject(new Error("Google verification expired"));
        }, 300_000);
        const closedCheck = window.setInterval(() => {
            if (!popup.closed) return;
            cleanup();
            reject(new Error("Google verification cancelled"));
        }, 500);
        function receive(event: MessageEvent<unknown>) {
            if (event.origin !== window.location.origin || event.source !== popup ||
                !isGoogleProofMessage(event.data) || event.data.state !== expectedState) return;
            cleanup();
            if (event.data.error || !event.data.value || !event.data.codeVerifier || !event.data.redirectUri) {
                reject(new Error("Google verification failed"));
                return;
            }
            resolve({ value: event.data.value, codeVerifier: event.data.codeVerifier, redirectUri: event.data.redirectUri });
        }
        window.addEventListener("message", receive);
    });
}

function proofToken(kind: ProofTokenKind, values: Partial<Record<ProofTokenKind, string>>,
    challenges: Partial<Record<ProofTokenKind, string>>, googleProof: GoogleProof | null): PresentedProofToken {
    if (kind === "GOOGLE") {
        if (!googleProof) throw new Error("Google proof required");
        return { kind, ...googleProof };
    }
    const value = values[kind];
    if (!value) throw new Error("Proof value required");
    return { kind, value: value.trim(), ...(challenges[kind] ? { challengeId: challenges[kind] } : {}) };
}

function selectedTokens(scheme: ActionSchemeOption, values: Partial<Record<ProofTokenKind, string>>,
    challenges: Partial<Record<ProofTokenKind, string>>, googleProof: GoogleProof | null): PresentedProofToken[] | null {
    if (scheme.requiredTokens.some(kind => EMAIL_KINDS.some(emailKind => emailKind === kind) && !challenges[kind])) return null;
    if (scheme.requiredTokens.some(kind => kind === "GOOGLE" ? !googleProof : !values[kind]?.trim())) return null;
    return scheme.requiredTokens.map(kind => proofToken(kind, values, challenges, googleProof));
}

async function requestMissingEmailCodes(action: AccountAction, scheme: ActionSchemeOption,
    challenges: Partial<Record<ProofTokenKind, string>>): Promise<Partial<Record<ProofTokenKind, string>>> {
    const result = { ...challenges };
    for (const kind of scheme.requiredTokens) {
        if (!EMAIL_KINDS.some(emailKind => emailKind === kind) || result[kind]) continue;
        const issued = await requestActionEmailCode(action, kind as typeof EMAIL_KINDS[number]);
        result[kind] = issued.challengeId;
    }
    return result;
}

export function useAuthenticationActionProof(action: AccountAction) {
    const [error, setError] = useState<ProofError>(null);
    const { schemes, scheme, setSchemeId, refresh, loading } = useAvailableSchemes(action, setError);
    const [values, setValues] = useState<Partial<Record<ProofTokenKind, string>>>({});
    const [challenges, setChallenges] = useState<Partial<Record<ProofTokenKind, string>>>({});
    const [googleProof, setGoogleProof] = useState<GoogleProof | null>(null);
    const [busy, setBusy] = useState(false);

    function selectScheme(id: string) {
        if (!schemes.some(option => option.id === id)) return;
        setSchemeId(id);
        setValues({});
        setChallenges({});
        setGoogleProof(null);
        setError(null);
    }

    function setValue(kind: ProofTokenKind, value: string) {
        setValues(current => ({ ...current, [kind]: value }));
        setError(null);
    }

    async function verifyWithGoogle(): Promise<void> {
        const popup = window.open("", "tallyvane-google-proof", "width=520,height=680");
        if (!popup) { setError("google"); return; }
        setBusy(true);
        setError(null);
        try {
            const start = await authClient.post<{ url: string }>("/google/proof/start", { action });
            const expectedState = new URL(start.url).searchParams.get("state");
            if (!expectedState) throw new Error("Google state missing");
            const verification = awaitGoogleProof(popup, expectedState);
            popup.location.assign(start.url);
            setGoogleProof(await verification);
        } catch {
            popup.close();
            setError("google");
        } finally {
            setBusy(false);
        }
    }

    async function sendEmailCodes(): Promise<void> {
        if (!scheme) { setError("required"); return; }
        setBusy(true);
        setError(null);
        try {
            setChallenges(await requestMissingEmailCodes(action, scheme, challenges));
        } catch {
            setError("invalid");
        } finally {
            setBusy(false);
        }
    }

    async function authorize(): Promise<string | null> {
        if (!scheme) { setError("required"); return null; }
        setBusy(true);
        setError(null);
        try {
            const tokens = selectedTokens(scheme, values, challenges, googleProof);
            if (!tokens) {
                setError("required");
                return null;
            }
            const result = await issueActionProof(action, tokens);
            setValues({});
            setChallenges({});
            setGoogleProof(null);
            return result.proof;
        } catch {
            setValues({});
            setChallenges({});
            setGoogleProof(null);
            setError("invalid");
            return null;
        } finally {
            setBusy(false);
        }
    }

    return { schemes, scheme, values, challenges, googleReady: googleProof !== null, loading, busy, error,
        selectScheme, setValue, verifyWithGoogle, sendEmailCodes, authorize, refresh };
}
