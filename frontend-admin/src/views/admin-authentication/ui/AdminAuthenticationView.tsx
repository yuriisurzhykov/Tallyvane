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
import { useAdminAuthenticationStrings } from "@/app/i18n";

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

const t = useAdminAuthenticationStrings("adminAuthentication");
const labels: Record<Primary | Factor | Requirement, string> = {
    PASSWORD: t("password"), GOOGLE: t("google"), EMAIL_CODE: t("emailCode"),
    TOTP: t("authenticator"), EMAIL_OTP: t("emailOtp"), BACKUP_CODE: t("backupCode"),
    DISABLED: t("disabled"), IF_ENROLLED: t("ifEnrolled"), REQUIRED: t("required"),
};
const factors: Factor[] = ["TOTP", "EMAIL_OTP", "BACKUP_CODE"];
const requirements: Requirement[] = ["DISABLED", "IF_ENROLLED", "REQUIRED"];

class RequestError extends Error {
    constructor(readonly status: number) {
        super();
    }
}

function failureText(reason: unknown): string {
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
        const message = failureText(reason);
        setError(message);
        actions.add({ title: t("settingsLoadFailed"), description: message, tone: "danger" });
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
            actions.add({ title: t("policySaved"), tone: "success" });
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
                title: t("factorsReset"),
                description: t("factorsResetDescription"),
                tone: "success"
            });
            setEmail("");
        } catch (reason) {
            fail(reason);
        } finally {
            setBusy(false);
        }
    }

    return <AppShell navItems={ adminNavItems("/authentication") } title={ t("title") }
                     skipLinkLabel={ t("skipLink") }>
        <div className="flex flex-col gap-stack">
            <Text variant="body">{ t("description") }</Text>
            { error && <div role="alert" className="flex flex-col gap-inline">
                <Text variant="body" tone="danger">{ error }</Text>
                { unauthorized && <Link href="/login?returnTo=/authentication">{ t("signInAdminLink") }</Link> }
                <Button tone="neutral" disabled={ busy || loading }
                        onClick={ () => void load() }>{ t("reloadPolicy") }</Button>
            </div> }
            { loading && <Text variant="body" role="status">{ t("loading") }</Text> }
            { !loading && policy && <>
                <fieldset disabled={ busy } className="flex flex-col gap-stack">
                    <legend className="sr-only">{ t("signInPolicy") }</legend>
                    { policy.rules.map(rule => <Panel key={ rule.primary } header={ <Text
                        variant="bodyStrong">{ labels[rule.primary] }</Text> }>
                        <div className="flex flex-col gap-stack">
                            <label className="flex items-center gap-inline"><Checkbox checked={ rule.enabled }
                                                                                      onCheckedChange={ enabled => update(rule.primary, { enabled }) }/>{ t("allowSignIn", { method: labels[rule.primary] }) }
                            </label>
                            <div className="flex flex-col gap-inline">
                                <Text variant="small"
                                      id={ `${ rule.primary }-requirement` }>{ t("secondFactorRequirement") }</Text>
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
                                <legend>{ t("allowedSecondFactors") }</legend>
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
                    <Text variant="body" role="alert" tone="danger">{ t("enablePolicyError") }</Text> }
                <div><Button tone="primary" loading={ busy } disabled={ invalid }
                             onClick={ () => risky ? setConfirmation("advanced") : void save(false) }>{ t("savePolicy") }</Button>
                </div>
                <Panel header={ <Text variant="bodyStrong">{ t("resetTitle") }</Text> }>
                    <form className="flex flex-col gap-stack" onSubmit={ event => {
                        event.preventDefault();
                        setConfirmation("reset");
                    } }>
                        <Text variant="body">{ t("resetHelp") }</Text>
                        <label htmlFor="reset-email">{ t("accountEmail") }</label>
                        <Input id="reset-email" type="email" required value={ email } disabled={ busy }
                               onChange={ event => setEmail(event.target.value) }/>
                        <div><Button tone="danger" type="submit"
                                     disabled={ busy || !email.trim() }>{ t("resetSecondFactors") }</Button></div>
                    </form>
                </Panel>
            </> }
        </div>
        <dialog ref={ dialog } onCancel={ () => setConfirmation(null) } onClose={ () => setConfirmation(null) }
                aria-labelledby="confirmation-title"
                className="m-auto max-w-prose rounded-control border border-border-default bg-surface-primary p-stack text-text-primary">
            <div className="flex flex-col gap-stack">
                <h2 id="confirmation-title">{ confirmation === "advanced" ? t("advancedWarningTitle") : t("resetConfirmationTitle") }</h2>
                <Text variant="body">{ confirmation === "advanced"
                    ? t("advancedWarningBody")
                    : t("resetConfirmationBody", { email: email.trim() }) }</Text>
                <div className="flex flex-wrap gap-inline">
                    <Button tone="neutral" autoFocus onClick={ () => setConfirmation(null) }>{ t("cancel") }</Button>
                    <Button tone="danger"
                            onClick={ () => confirmation === "advanced" ? void save(true) : void reset() }>{ confirmation === "advanced" ? t("acceptRiskSave") : t("resetAndRevoke") }</Button>
                </div>
            </div>
        </dialog>
    </AppShell>;
}
