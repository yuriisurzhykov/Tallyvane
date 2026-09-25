"use client";

import { useCallback, useEffect, useState } from "react";
import {
    issueActionProof, readActionSchemes, requestActionEmailCode,
    type AccountAction, type ActionSchemeOption, type PresentedProofToken, type ProofTokenKind,
} from "../../../features/authentication/api/actionProof";
import { authClient } from "../../../features/authentication/api/client";

type GoogleProof = { value: string; codeVerifier: string; redirectUri: string };
type GoogleProofMessage = { type: "tallyvane-google-action-proof"; state: string; error: boolean } & GoogleProof;
const EMAIL_KINDS = ["EMAIL_SIGN_IN_CODE", "EMAIL_FACTOR_CODE"] as const;

export function useAuthenticationActionProof(action: AccountAction) {
    const [schemes, setSchemes] = useState<ActionSchemeOption[]>([]);
    const [schemeId, setSchemeId] = useState("");
    const [values, setValues] = useState<Partial<Record<ProofTokenKind, string>>>({});
    const [challenges, setChallenges] = useState<Partial<Record<ProofTokenKind, string>>>({});
    const [googleProof, setGoogleProof] = useState<GoogleProof | null>(null);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<"load" | "required" | "invalid" | "google" | null>(null);

    const refresh = useCallback(async () => {
        setLoading(true);
        setError(null);
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
    }, [action]);

    useEffect(() => { void refresh(); }, [refresh]);

    const scheme = schemes.find(option => option.id === schemeId) ?? null;

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
            const verification = new Promise<GoogleProof>((resolve, reject) => {
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
                    if (popup.closed) {
                        cleanup();
                        reject(new Error("Google verification cancelled"));
                    }
                }, 500);
                function receive(event: MessageEvent<GoogleProofMessage>) {
                    if (event.origin !== window.location.origin || event.source !== popup ||
                        event.data?.type !== "tallyvane-google-action-proof" || event.data.state !== expectedState) return;
                    cleanup();
                    if (event.data.error || !event.data.value || !event.data.codeVerifier || !event.data.redirectUri) {
                        reject(new Error("Google verification failed"));
                        return;
                    }
                    resolve({ value: event.data.value, codeVerifier: event.data.codeVerifier, redirectUri: event.data.redirectUri });
                }
                window.addEventListener("message", receive);
            });
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
            const emailKinds = scheme.requiredTokens.filter(
                (kind): kind is typeof EMAIL_KINDS[number] => EMAIL_KINDS.some(emailKind => emailKind === kind),
            );
            const missingChallenges = emailKinds.filter(kind => !challenges[kind]);
            for (const kind of missingChallenges) {
                const issued = await requestActionEmailCode(action, kind);
                setChallenges(current => ({ ...current, [kind]: issued.challengeId }));
            }
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
            if (scheme.requiredTokens.some(kind => EMAIL_KINDS.some(emailKind => emailKind === kind) && !challenges[kind])) {
                setError("required");
                return null;
            }
            if (scheme.requiredTokens.some(kind => kind === "GOOGLE" ? !googleProof : !values[kind]?.trim())) {
                setError("required");
                return null;
            }
            const tokens: PresentedProofToken[] = scheme.requiredTokens.map(kind => kind === "GOOGLE"
                ? { kind, ...googleProof! }
                : { kind, value: values[kind]!.trim(), ...(challenges[kind] ? { challengeId: challenges[kind] } : {}) });
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
