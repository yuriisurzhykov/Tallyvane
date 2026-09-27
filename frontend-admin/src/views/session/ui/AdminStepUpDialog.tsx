"use client";

import { useEffect, useState, type SyntheticEvent } from "react";
import { Button } from "frontend-shared/ui/button";
import { Drawer } from "frontend-shared/ui/drawer";
import { Field } from "frontend-shared/ui/field";
import { Form } from "frontend-shared/ui/form";
import { Input } from "frontend-shared/ui/input";
import { Select } from "frontend-shared/ui/select";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import type { StepUpProblem } from "frontend-shared/api";
import {
    adminAuthClient,
    adminSessionRuntime,
    type AdminAccountAction,
    type AdminPresentedProofToken,
    type AdminProofScheme,
    type AdminProofTokenKind,
} from "@/features/admin-login";
import { useAdminLoginStrings } from "@/features/admin-login";
import styles from "./AdminSessionGate.module.css";

const supportedActions: readonly AdminAccountAction[] = ["CHANGE_PRIMARY_CREDENTIAL", "MANAGE_SECOND_FACTORS"];
const emailKinds: readonly AdminProofTokenKind[] = ["EMAIL_SIGN_IN_CODE", "EMAIL_FACTOR_CODE"];
interface GoogleProof { readonly value: string; readonly codeVerifier: string; readonly redirectUri: string }
type GoogleProofMessage = { readonly type: "tallyvane-google-action-proof"; readonly state: string; readonly error: boolean } & GoogleProof;

export function AdminStepUpDialog({ problem }: { readonly problem: StepUpProblem }) {
    const t = useAdminLoginStrings("adminLogin");
    const action = supportedActions.find(value => value === problem.action);
    const [schemes, setSchemes] = useState<AdminProofScheme[]>([]);
    const [schemeId, setSchemeId] = useState("");
    const [values, setValues] = useState<Partial<Record<AdminProofTokenKind, string>>>({});
    const [challenges, setChallenges] = useState<Partial<Record<AdminProofTokenKind, string>>>({});
    const [googleProof, setGoogleProof] = useState<GoogleProof | null>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState(action ? "" : t("stepUpUnavailable"));
    const [notice, setNotice] = useState("");

    useEffect(() => {
        if (!action) return;
        let active = true;
        void adminAuthClient.readActionSchemes(action).then(result => {
            if (!active) return;
            setSchemes(result);
            setSchemeId(result[0]?.id ?? "");
            if (result.length === 0) setError(t("stepUpUnavailable"));
        }).catch(() => {
            if (active) setError(t("stepUpLoadFailed"));
        });
        return () => { active = false; };
    }, [action, t]);

    const scheme = schemes.find(value => value.id === schemeId);

    async function verifyWithGoogle() {
        if (!action) return;
        const popup = window.open("", "tallyvane-google-proof", "width=520,height=680");
        if (!popup) { setError(t("stepUpGoogleFailed")); return; }
        setBusy(true);
        setError("");
        try {
            const start = await adminAuthClient.startGoogleActionProof(action);
            const expectedState = new URL(start.url).searchParams.get("state");
            if (!expectedState) throw new Error("Google state missing");
            const verification = new Promise<GoogleProof>((resolve, reject) => {
                function cleanup() {
                    window.clearTimeout(timeout);
                    window.clearInterval(closedCheck);
                    window.removeEventListener("message", receive);
                }
                const timeout = window.setTimeout(() => { cleanup(); reject(new Error("Google verification expired")); }, 300_000);
                const closedCheck = window.setInterval(() => {
                    if (popup.closed) { cleanup(); reject(new Error("Google verification cancelled")); }
                }, 500);
                function receive(event: MessageEvent<GoogleProofMessage>) {
                    if (event.origin !== window.location.origin || event.source !== popup ||
                        event.data.state !== expectedState) return;
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
            setError(t("stepUpGoogleFailed"));
        } finally {
            setBusy(false);
        }
    }

    async function sendEmailCodes() {
        if (!action || !scheme) return;
        setBusy(true);
        setError("");
        try {
            for (const kind of scheme.requiredTokens.filter(token => emailKinds.includes(token))) {
                if (challenges[kind]) continue;
                const issued = await adminAuthClient.requestActionEmailCode(action, kind as "EMAIL_SIGN_IN_CODE" | "EMAIL_FACTOR_CODE");
                setChallenges(current => ({ ...current, [kind]: issued.challengeId }));
            }
            setNotice(t("stepUpCodeSent"));
        } catch {
            setError(t("requestFailed"));
        } finally {
            setBusy(false);
        }
    }

    async function submit(event: SyntheticEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!action || !scheme || busy) return;
        const tokens: AdminPresentedProofToken[] = [];
        for (const kind of scheme.requiredTokens) {
            if (kind === "GOOGLE") {
                if (!googleProof) { setError(t("stepUpUnavailable")); return; }
                tokens.push({ kind, ...googleProof });
                continue;
            }
            const value = values[kind]?.trim();
            if (!value || (emailKinds.includes(kind) && !challenges[kind])) {
                setError(t("stepUpUnavailable"));
                return;
            }
            tokens.push({ kind, value, ...(challenges[kind] ? { challengeId: challenges[kind] } : {}) });
        }
        setBusy(true);
        setError("");
        try {
            const result = await adminAuthClient.issueActionProof(action, tokens);
            adminSessionRuntime.completeStepUp(result.proof);
        } catch {
            setError(t("invalidCode"));
        } finally {
            setBusy(false);
        }
    }

    const tokenLabel = (kind: AdminProofTokenKind) => {
        if (kind === "PASSWORD") return t("password");
        if (kind === "TOTP") return t("methodTotp");
        if (kind === "EMAIL_FACTOR_CODE") return t("methodEmail");
        if (kind === "GOOGLE") return t("stepUpGoogle");
        return t("email");
    };

    return <Drawer.Root open>
        <Drawer.Popup className={styles.stepUpPanel ?? ""}>
            <Drawer.Title>{t("stepUpTitle")}</Drawer.Title>
            <Drawer.Description>{t("stepUpDescription")}</Drawer.Description>
            {schemes.length > 1 && <Field label={t("verificationTitle")}>
                <Select.Root value={schemeId} disabled={busy} onValueChange={value => {
                    setSchemeId(value ?? "");
                    setValues({});
                    setChallenges({});
                    setGoogleProof(null);
                    setError("");
                    setNotice("");
                }}>
                    <Select.Trigger><Select.Value /><Select.Icon /></Select.Trigger>
                    <Select.Popup>{schemes.map(value => <Select.Item key={value.id} value={value.id}>
                        {value.requiredTokens.map(tokenLabel).join(" + ")} · {t("stepUpRank", { rank: value.assuranceRank })}
                    </Select.Item>)}</Select.Popup>
                </Select.Root>
            </Field>}
            {scheme && <Form onSubmit={event => { void submit(event); }}>
                <Stack gap="inline-tight">
                    {scheme.requiredTokens.map(kind => <Field key={kind} label={tokenLabel(kind)}>
                        {kind === "GOOGLE" ? <Stack gap="inline-tight">
                            <Button type="button" tone="neutral" disabled={busy} onClick={() => { void verifyWithGoogle(); }}>{t("stepUpGoogle")}</Button>
                            {googleProof && <Text role="status" variant="small">{t("stepUpGoogleReady")}</Text>}
                        </Stack> : emailKinds.includes(kind) && !challenges[kind] ?
                            <Text variant="small" color="muted">{t("stepUpSendCode")}</Text> :
                            <Input required type={kind === "PASSWORD" ? "password" : "text"}
                                autoComplete={kind === "PASSWORD" ? "current-password" : "one-time-code"}
                                value={values[kind] ?? ""} disabled={busy}
                                onChange={event => { setValues(current => ({ ...current, [kind]: event.target.value })); }} />}
                    </Field>)}
                    {notice && <Text role="status" variant="small">{notice}</Text>}
                    {error && <Text role="alert" variant="small">{error}</Text>}
                    <Stack gap="inline-tight">
                        {scheme.requiredTokens.some(kind => emailKinds.includes(kind) && !challenges[kind]) &&
                            <Button type="button" tone="neutral" disabled={busy} onClick={() => { void sendEmailCodes(); }}>{t("stepUpSendCode")}</Button>}
                        <Button type="submit" tone="primary" loading={busy}>{t("stepUpSubmit")}</Button>
                    </Stack>
                </Stack>
            </Form>}
            {!scheme && !error && <Text role="status" variant="small">{t("checkingSession")}</Text>}
        </Drawer.Popup>
    </Drawer.Root>;
}
