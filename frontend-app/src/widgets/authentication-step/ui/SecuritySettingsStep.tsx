"use client";

import { useEffect, useState, type ReactNode, type SubmitEvent } from "react";
import { authClient } from "../../../features/authentication/api/client";
import { useToast } from "frontend-shared/ui/toast";
import { Button } from "frontend-shared/ui/button";
import { Form } from "frontend-shared/ui/form";
import { Input } from "frontend-shared/ui/input";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import type { AuthSession, AuthStepProps } from "../model/AuthStepProps";
import { useFactorStatus } from "../model/useFactorStatus";
import { FactorManagementSection } from "./FactorManagementSection";
import { AuthenticationActionForm } from "./AuthenticationActionForm";
import { AuthField } from "./AuthField";
import { GoogleAccountSection } from "./GoogleAccountSection";
import styles from "./auth-step.module.css";

type SecurityAction = "totp" | "password" | "google" | "email" | "recovery" | "sessions";

export function SecuritySettingsStep({ props }: { props: AuthStepProps }) {
    const { t } = props;
    const [selected, setSelected] = useState<SecurityAction>("totp");
    const [googleAvailable, setGoogleAvailable] = useState(false);
    const [googleLinked, setGoogleLinked] = useState(false);
    const factors = useFactorStatus(t, 0);
    const activeSessions = props.sessions.filter(session => !session.revokedAt);
    const onFactorChanged = factors.refreshStatus;

    useEffect(() => {
        let active = true;
        void authClient.get<{ google?: boolean }>("/providers").then(async providers => {
            if (!providers.google) return;
            const status = await authClient.get<{ googleLinked: boolean }>("/google/status");
            if (active) { setGoogleAvailable(true); setGoogleLinked(status.googleLinked); }
        }).catch(() => { if (active) setGoogleAvailable(false); });
        return () => { active = false; };
    }, []);

    return <div className={styles.securityGrid}>
        <div className={styles.securityOverview}>
            <SecurityGroup title={t("methods")}>
                <SecurityItem title={t("securityEmailSignIn")} description={t("securityEmailSignInHelp")}
                    badge={t("securityAvailable")} badgeTone="good" />
                <SecurityItem title={t("password")} description={t("securityPasswordHelp")}
                    action={t("changePassword")} active={selected === "password"} onAction={() => { setSelected("password"); }} />
                {googleAvailable && <SecurityItem title="Google" description={t("securityGoogleHelp")}
                    badge={t(googleLinked ? "securityConnected" : "securityNotConnected")}
                    badgeTone={googleLinked ? "good" : undefined}
                    action={t(googleLinked ? "securityManage" : "linkGoogle")}
                    active={selected === "google"} onAction={() => { setSelected("google"); }} />}
            </SecurityGroup>
            <SecurityGroup title={t("securityExtraProtection")} hint={t("securityOptional")}>
                <SecurityItem title={t("factorAuthenticator")} description={t("securityAuthenticatorHelp")}
                    badge={t(factors.enrolled.includes("TOTP") ? "securityConnected" : "securityNotConnected")}
                    badgeTone={factors.enrolled.includes("TOTP") ? "good" : undefined}
                    action={t(factors.enrolled.includes("TOTP") ? "securityManage" : "securityConnect")}
                    active={selected === "totp"} onAction={() => { setSelected("totp"); }} />
                <SecurityItem title={t("factorEmail")} description={t("securityEmailFactorHelp")}
                    badge={t(factors.enrolled.includes("EMAIL_OTP") ? "securityConnected" : "securityNotConnected")}
                    badgeTone={factors.enrolled.includes("EMAIL_OTP") ? "good" : undefined}
                    action={t(factors.enrolled.includes("EMAIL_OTP") ? "securityManage" : "securityConnect")}
                    active={selected === "email"} onAction={() => { setSelected("email"); }} />
            </SecurityGroup>
            <SecurityGroup title={t("securityRecoveryAndSessions")}>
                <SecurityItem title={t("recoveryCodesTitle")} description={t("securityRecoveryHelp")}
                    badge={t(factors.enrolled.includes("BACKUP_CODE") ? "securityCreated" : "securityNotCreated")}
                    action={t("securityManage")} active={selected === "recovery"}
                    onAction={() => { setSelected("recovery"); }} />
                <SecurityItem title={t("sessions")} description={t("securitySessionCount", { count: activeSessions.length })}
                    action={t("securityManage")} active={selected === "sessions"}
                    onAction={() => { setSelected("sessions"); }} />
            </SecurityGroup>
        </div>
        <aside className={styles.securityDetail} aria-label={t("securitySelectedAction")}>
            {selected === "totp" && <FactorManagementSection t={t} enrolled={factors.enrolled.includes("TOTP")}
                onFactorChanged={onFactorChanged} />}
            {selected === "password" && <PasswordPanel props={props} />}
            {selected === "google" && googleAvailable && <GoogleAccountSection t={t} linked={googleLinked} onLinkedChange={setGoogleLinked} />}
            {selected === "email" && <EmailMfaPanel props={props} enrolled={factors.enrolled.includes("EMAIL_OTP")}
                onFactorChanged={onFactorChanged} />}
            {selected === "recovery" && <RecoveryCodesPanel props={props} onFactorChanged={onFactorChanged} />}
            {selected === "sessions" && <SessionsPanel props={props} sessions={activeSessions} />}
        </aside>
    </div>;
}

function SecurityGroup({ title, hint, children }: { title: string; hint?: string; children: ReactNode }) {
    return <section className={styles.securityCard}>
        <div className={styles.securityGroupHeading}><h2>{title}</h2>{hint && <span>{hint}</span>}</div>
        <div>{children}</div>
    </section>;
}

function SecurityItem({ title, description, badge, badgeTone, action, active, onAction }: {
    title: string; description: string; badge?: string; badgeTone?: "good" | undefined;
    action?: string; active?: boolean; onAction?: () => void;
}) {
    return <div className={styles.securityItem}>
        <div className={styles.securityItemCopy}><strong>{title}</strong><small>{description}</small></div>
        <div className={styles.securityItemActions}>
            {badge && <span className={badgeTone === "good" ? styles.securityBadgeGood : styles.securityBadge}>{badge}</span>}
            {action && <button type="button" className={styles.securityAction} aria-pressed={active}
                onClick={onAction}>{action}</button>}
        </div>
    </div>;
}

function PasswordPanel({ props }: { props: AuthStepProps }) {
    const { t } = props;
    const { actions } = useToast();
    return <Stack gap="stack" as="section" aria-labelledby="password-panel-title">
        <Text as="h2" variant="title2" id="password-panel-title">{t("changePassword")}</Text>
        <Text variant="body" color="muted">{t("securityPasswordChangeHelp")}</Text>
        <AuthenticationActionForm action="CHANGE_PRIMARY_CREDENTIAL" t={t} submitLabel={t("changePasswordAction")}
            onAuthorized={async (proof, form) => {
                await authClient.postWithHeaders("/account/password", { newPassword: String(form.get("newPassword") ?? "") },
                    { "X-Action-Proof": proof });
                actions.add({ title: t("passwordChanged"), tone: "success" });
            }}>
            <AuthField name="newPassword" label={t("newPassword")}
                control={<Input type="password" autoComplete="new-password" minLength={15} maxLength={128} required />}
                errors={props.fieldErrors} help={t("passwordHelp")} />
        </AuthenticationActionForm>
    </Stack>;
}

function EmailMfaPanel({ props, enrolled, onFactorChanged }: {
    props: AuthStepProps; enrolled: boolean; onFactorChanged: () => Promise<void>;
}) {
    const { t } = props;
    const [challengeId, setChallengeId] = useState("");
    const [removeRequested, setRemoveRequested] = useState(false);
    const [confirming, setConfirming] = useState(false);
    const { actions } = useToast();
    async function confirmEmail(event: SubmitEvent<HTMLFormElement>) {
        event.preventDefault();
        setConfirming(true);
        try {
            await authClient.post("/mfa/email/confirm", {
                challengeId, code: String(new FormData(event.currentTarget).get("emailMfaCode") ?? ""),
            });
            setChallengeId("");
            await onFactorChanged();
            actions.add({ title: t("emailMfaEnabled"), tone: "success" });
        } catch {
            actions.add({ title: t("factorEnrollmentFailed"), tone: "danger" });
        } finally {
            setConfirming(false);
        }
    }
    return <Stack gap="stack" as="section" aria-labelledby="email-factor-panel-title">
        <Text as="h2" variant="title2" id="email-factor-panel-title">{t("emailMfaTitle")}</Text>
        <Text variant="body" color="muted">{t(enrolled ? "securityEmailFactorActive" : "emailMfaDescription")}</Text>
        {enrolled ? <>
            {!removeRequested ? <Button tone="neutral" type="button" onClick={() => { setRemoveRequested(true); }}>
                {t("remove")}
            </Button> : <>
                <Text variant="body">{t("disableFactorDescription")}</Text>
                <AuthenticationActionForm action="MANAGE_SECOND_FACTORS" t={t} tone="danger"
                    submitLabel={t("disableFactorConfirm")}
                    onAuthorized={async proof => {
                        await authClient.postWithHeaders("/mfa/disable", { kind: "EMAIL_OTP", confirmed: true },
                            { "X-Action-Proof": proof });
                        setRemoveRequested(false);
                        await onFactorChanged();
                        actions.add({ title: t("factorDisabled", { factor: t("factorEmail") }), tone: "success" });
                    }} />
                <Button tone="ghost" type="button" onClick={() => { setRemoveRequested(false); }}>{t("cancel")}</Button>
            </>}
        </> : challengeId ? <Form onSubmit={event => { void confirmEmail(event); }}>
            <AuthField name="emailMfaCode" label={t("emailMfaCode")}
                control={<Input className={styles.codeInput ?? ""} inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} required />}
                errors={props.fieldErrors} />
            <Button tone="primary" type="submit" loading={confirming}>{t("emailMfaConfirm")}</Button>
        </Form> : <AuthenticationActionForm action="MANAGE_SECOND_FACTORS" t={t}
            submitLabel={t("emailMfaEnable")}
            onAuthorized={async proof => {
                const result = await authClient.postWithHeaders<{ challengeId: string }>("/mfa/email/enroll", {},
                    { "X-Action-Proof": proof });
                setChallengeId(result.challengeId);
                actions.add({ title: t("emailMfaCodeSent"), description: t("emailMfaCodeSentHelp"), tone: "success" });
            }} />}
    </Stack>;
}

function RecoveryCodesPanel({ props, onFactorChanged }: { props: AuthStepProps; onFactorChanged: () => Promise<void> }) {
    const { t } = props;
    const [codes, setCodes] = useState<string[]>([]);
    const { actions } = useToast();
    return <Stack gap="stack" as="section" aria-labelledby="recovery-panel-title">
        <Text as="h2" variant="title2" id="recovery-panel-title">{t("recoveryCodesTitle")}</Text>
        <Text variant="body" color="muted">{t("backupWarning")}</Text>
        {codes.length === 0 ? <AuthenticationActionForm action="MANAGE_SECOND_FACTORS" t={t} submitLabel={t("backupGenerate")}
            onAuthorized={async proof => {
                const result = await authClient.postWithHeaders<{ codes: string[] }>("/mfa/backup-codes", {},
                    { "X-Action-Proof": proof });
                setCodes(result.codes);
                await onFactorChanged();
                actions.add({ title: t("recoveryCodesIssued"), description: t("recoveryCodesOneTime"), tone: "success" });
            }} /> : <Stack gap="stack-tight" role="status">
            <Text variant="small" color="muted">{t("recoveryCodesOneTime")}</Text>
            <ul className={styles.securityCodes}>{codes.map(code => <li key={code}><code>{code}</code></li>)}</ul>
            <Button tone="ghost" size="sm" type="button" onClick={() => { setCodes([]); }}>
                {t("backupCodesSaved")}
            </Button>
        </Stack>}
    </Stack>;
}

function SessionsPanel({ props, sessions }: { props: AuthStepProps; sessions: AuthSession[] }) {
    return <Stack gap="stack" as="section" aria-labelledby="sessions-panel-title">
        <Text as="h2" variant="title2" id="sessions-panel-title">{props.t("sessions")}</Text>
        {sessions.length === 0 ? <Text variant="body" color="muted">{props.t("noSessions")}</Text> :
            <ul className={styles.securitySessions}>{sessions.map(session => <li key={session.id}>
                <div><strong>{session.device}</strong><small>{props.t("lastUsed", { date: formatDate(session.lastUsedAt) })}</small></div>
                <Button tone="ghost" size="sm" type="button" onClick={() => { props.revokeSession(session.id); }}>
                    {props.t("revoke")}
                </Button>
            </li>)}</ul>}
    </Stack>;
}

function formatDate(value: string) {
    return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}
