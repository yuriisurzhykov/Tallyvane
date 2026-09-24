"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { AppShell } from "frontend-shared/ui/app-shell";
import { Button } from "frontend-shared/ui/button";
import { Checkbox } from "frontend-shared/ui/checkbox";
import { Input } from "frontend-shared/ui/input";
import { Panel } from "frontend-shared/ui/panel";
import { Select } from "frontend-shared/ui/select";
import { Text } from "frontend-shared/ui/text";
import { useToast } from "frontend-shared/ui/toast";
import { adminNavItems } from "@/app/navigation";

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

const labels: Record<Primary | Factor | Requirement, string> = {
    PASSWORD: "Password", GOOGLE: "Google", EMAIL_CODE: "Email sign-in code",
    TOTP: "Authenticator app", EMAIL_OTP: "Email verification code", BACKUP_CODE: "Backup code",
    DISABLED: "No second factor", IF_ENROLLED: "When enrolled", REQUIRED: "Always required",
};
const factors: Factor[] = ["TOTP", "EMAIL_OTP", "BACKUP_CODE"];
const requirements: Requirement[] = ["DISABLED", "IF_ENROLLED", "REQUIRED"];

class RequestError extends Error {
    constructor(readonly status: number) {
        super(status === 401 ? "Sign in to your administrator account." : status === 403
            ? "Your account does not have administrator access." : status === 409
                ? "This policy changed elsewhere. Reload it before saving again." : "The request failed. Please try again.");
    }
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
    const [policy, setPolicy] = useState<Policy | null>(null);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [unauthorized, setUnauthorized] = useState(false);
    const [email, setEmail] = useState("");
    const [confirmation, setConfirmation] = useState<"advanced" | "reset" | null>(null);
    const dialog = useRef<HTMLDialogElement>(null);
    const { actions } = useToast();
    const fail = useCallback((reason: unknown) => {
        const message = reason instanceof Error ? reason.message : "Could not connect. Please try again.";
        setError(message);
        actions.add({ title: "Authentication settings could not be loaded", description: message, tone: "danger" });
        setUnauthorized(reason instanceof RequestError && reason.status === 401);
    }, [actions]);
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
        void load();
    }, [load]);
    useEffect(() => {
        if (confirmation) dialog.current?.showModal();
        else dialog.current?.close();
    }, [confirmation]);
    const update = (primary: Primary, patch: Partial<Rule>) => setPolicy(current => current && ({
        ...current, advancedAcknowledged: false,
        rules: current.rules.map(rule => rule.primary === primary ? { ...rule, ...patch } : rule),
    }));
    const invalid = policy !== null && (!policy.rules.some(rule => rule.enabled) ||
        policy.rules.some(rule => rule.enabled && rule.requirement !== "DISABLED" && rule.allowedMethods.length === 0));
    const risky = policy?.rules.some(rule => rule.enabled && rule.primary !== "PASSWORD" &&
        rule.requirement !== "DISABLED" && rule.allowedMethods.includes("EMAIL_OTP"));

    async function save(acknowledged: boolean) {
        if (!policy || invalid) return;
        setConfirmation(null);
        setBusy(true);
        setError(null);
        try {
            await request<unknown>("policy", "PUT", {
                expectedVersion: policy.version, rules: policy.rules, advancedAcknowledged: acknowledged,
            });
            const saved = await request<Policy>("policy");
            setPolicy(saved);
            actions.add({ title: "Authentication policy saved", tone: "success" });
        } catch (reason) {
            fail(reason);
        } finally {
            setBusy(false);
        }
    }

    async function reset() {
        setConfirmation(null);
        setBusy(true);
        setError(null);
        try {
            await request<unknown>("mfa/reset", "POST", { email: email.trim(), confirmation: true });
            actions.add({
                title: "Second factors reset",
                description: "Sessions revoked. The user must sign in and enroll again.",
                tone: "success"
            });
            setEmail("");
        } catch (reason) {
            fail(reason);
        } finally {
            setBusy(false);
        }
    }

    return <AppShell navItems={ adminNavItems("/authentication") } title="Authentication"
                     skipLinkLabel="Skip to content">
        <div className="flex flex-col gap-stack">
            <Text variant="body">Choose how people sign in and which second factors they need. Changes apply to future
                sign-ins.</Text>
            { error && <div role="alert" className="flex flex-col gap-inline">
                <Text variant="body" tone="danger">{ error }</Text>
                { unauthorized && <Link href="/login?returnTo=/authentication">Sign in to administration</Link> }
                <Button tone="neutral" disabled={ busy || loading } onClick={ () => void load() }>Reload policy</Button>
            </div> }
            { loading && <Text variant="body" role="status">Loading authentication policy…</Text> }
            { !loading && policy && <>
                <fieldset disabled={ busy } className="flex flex-col gap-stack">
                    <legend className="sr-only">Sign-in policy</legend>
                    { policy.rules.map(rule => <Panel key={ rule.primary } header={ <Text
                        variant="bodyStrong">{ labels[rule.primary] }</Text> }>
                        <div className="flex flex-col gap-stack">
                            <label className="flex items-center gap-inline"><Checkbox checked={ rule.enabled }
                                                                                      onCheckedChange={ enabled => update(rule.primary, { enabled }) }/>Allow { labels[rule.primary].toLowerCase() } sign-in</label>
                            <div className="flex flex-col gap-inline">
                                <Text variant="small" id={ `${ rule.primary }-requirement` }>Second-factor
                                    requirement</Text>
                                <Select.Root value={ rule.requirement } disabled={ !rule.enabled }
                                             onValueChange={ value => {
                                                 if (value) update(rule.primary, { requirement: value as Requirement });
                                             } }>
                                    <Select.Trigger
                                        aria-labelledby={ `${ rule.primary }-requirement` }><Select.Value>{ labels[rule.requirement] }</Select.Value><Select.Icon/></Select.Trigger>
                                    <Select.Popup>{ requirements.map(value => <Select.Item key={ value }
                                                                                           value={ value }>{ labels[value] }</Select.Item>) }</Select.Popup>
                                </Select.Root>
                            </div>
                            <fieldset disabled={ !rule.enabled || rule.requirement === "DISABLED" }
                                      className="flex flex-wrap gap-stack">
                                <legend>Allowed second factors</legend>
                                { factors.map(factor => <label key={ factor } className="flex items-center gap-inline">
                                    <Checkbox checked={ rule.allowedMethods.includes(factor) }
                                              onCheckedChange={ checked => update(rule.primary, {
                                                  allowedMethods: checked ? [...rule.allowedMethods, factor] : rule.allowedMethods.filter(item => item !== factor),
                                              }) }/>{ labels[factor] }
                                </label>) }
                            </fieldset>
                        </div>
                    </Panel>) }
                </fieldset>
                { invalid &&
                    <Text variant="body" role="alert" tone="danger">Enable at least one sign-in method and choose a
                        factor for each active second-factor rule.</Text> }
                <div><Button tone="primary" loading={ busy } disabled={ invalid }
                             onClick={ () => risky ? setConfirmation("advanced") : void save(false) }>Save
                    policy</Button></div>
                <Panel header={ <Text variant="bodyStrong">Reset a user’s second factors</Text> }>
                    <form className="flex flex-col gap-stack" onSubmit={ event => {
                        event.preventDefault();
                        setConfirmation("reset");
                    } }>
                        <Text variant="body">This revokes sessions and removes enrolled factors. Verify the user’s
                            identity before proceeding.</Text>
                        <label htmlFor="reset-email">Account email</label>
                        <Input id="reset-email" type="email" required value={ email } disabled={ busy }
                               onChange={ event => setEmail(event.target.value) }/>
                        <div><Button tone="danger" type="submit" disabled={ busy || !email.trim() }>Reset second
                            factors…</Button></div>
                    </form>
                </Panel>
            </> }
        </div>
        <dialog ref={ dialog } onCancel={ () => setConfirmation(null) } onClose={ () => setConfirmation(null) }
                aria-labelledby="confirmation-title"
                className="m-auto max-w-prose rounded-control border border-border-default bg-surface-primary p-stack text-text-primary">
            <div className="flex flex-col gap-stack">
                <h2 id="confirmation-title">{ confirmation === "advanced" ? "Allow a less independent second factor?" : "Reset second factors?" }</h2>
                <Text variant="body">{ confirmation === "advanced"
                    ? "Email after Google or email-code sign-in can depend on the same inbox. Someone who controls that inbox may pass both steps. Save only if you explicitly accept this risk."
                    : `Reset all second factors for ${ email.trim() }? Their sessions will be revoked and they must enroll again. This cannot be undone.` }</Text>
                <div className="flex flex-wrap gap-inline">
                    <Button tone="neutral" autoFocus onClick={ () => setConfirmation(null) }>Cancel</Button>
                    <Button tone="danger"
                            onClick={ () => confirmation === "advanced" ? void save(true) : void reset() }>{ confirmation === "advanced" ? "Accept risk and save" : "Reset and revoke sessions" }</Button>
                </div>
            </div>
        </dialog>
    </AppShell>;
}
