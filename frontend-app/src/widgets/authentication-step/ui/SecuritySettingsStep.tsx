"use client";

import { useEffect, useState, type SubmitEvent } from "react";
import { authClient } from "../../../features/authentication/api/client";
import { useToast } from "frontend-shared/ui/toast";
import { Button } from "frontend-shared/ui/button";
import { Form } from "frontend-shared/ui/form";
import { Input } from "frontend-shared/ui/input";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { SettingsItem } from "settings-kit/entities/settings-item";
import { useOptionalSettingsUnsavedChanges } from "settings-kit/features/settings-navigation";
import { SettingsGroup } from "settings-kit/widgets/settings-group";
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

    return <Stack gap="stack" className={styles.securityGrid ?? ""}>
        <SecurityOverview props={props} selected={selected} select={setSelected} factors={factors}
            googleAvailable={googleAvailable} googleLinked={googleLinked} sessionCount={activeSessions.length} />
        <SecurityDetail props={props} selected={selected} factors={factors} googleAvailable={googleAvailable}
            googleLinked={googleLinked} setGoogleLinked={setGoogleLinked} sessions={activeSessions}
            onFactorChanged={onFactorChanged} />
    </Stack>;
}

interface SecurityPanelProps {
    readonly props: AuthStepProps;
    readonly selected: SecurityAction;
    readonly factors: ReturnType<typeof useFactorStatus>;
    readonly googleAvailable: boolean;
    readonly googleLinked: boolean;
}

function SecurityOverview(options: SecurityPanelProps & {
    readonly select: (action: SecurityAction) => void;
    readonly sessionCount: number;
}) {
    const { props, selected, select, factors, googleAvailable, googleLinked, sessionCount } = options;
    const { t } = props;
    return <Stack gap="stack" className={styles.securityOverview ?? ""}>
        <SettingsGroup title={t("methods")}>
            <SecurityItem title={t("securityEmailSignIn")} description={t("securityEmailSignInHelp")}
                badge={t("securityAvailable")} badgeTone="good" />
            <SecurityItem title={t("password")} description={t("securityPasswordHelp")}
                action={t("changePassword")} active={selected === "password"} onAction={() => { select("password"); }} />
            {googleAvailable && <SecurityItem title="Google" description={t("securityGoogleHelp")}
                badge={t(googleLinked ? "securityConnected" : "securityNotConnected")}
                {...(googleLinked ? { badgeTone: "good" as const } : {})}
                action={t(googleLinked ? "securityManage" : "linkGoogle")}
                active={selected === "google"} onAction={() => { select("google"); }} />}
        </SettingsGroup>
        <SettingsGroup title={t("securityExtraProtection")} description={t("securityOptional")}>
            <FactorSecurityItem kind="TOTP" selected="totp" options={options} />
            <FactorSecurityItem kind="EMAIL_OTP" selected="email" options={options} />
        </SettingsGroup>
        <SettingsGroup title={t("securityRecoveryAndSessions")}>
            <SecurityItem title={t("recoveryCodesTitle")} description={t("securityRecoveryHelp")}
                badge={t(factors.recoveryCodesIssued ? "securityCreated" : "securityNotCreated")}
                action={t("securityManage")} active={selected === "recovery"} onAction={() => { select("recovery"); }} />
            <SecurityItem title={t("sessions")} description={t("securitySessionCount", { count: sessionCount })}
                action={t("securityManage")} active={selected === "sessions"} onAction={() => { select("sessions"); }} />
        </SettingsGroup>
    </Stack>;
}

function FactorSecurityItem({ kind, selected, options }: {
    readonly kind: "TOTP" | "EMAIL_OTP";
    readonly selected: Extract<SecurityAction, "totp" | "email">;
    readonly options: SecurityPanelProps & { readonly select: (action: SecurityAction) => void };
}) {
    const enrolled = options.factors.enrolled.includes(kind);
    const title = kind === "TOTP" ? "factorAuthenticator" : "factorEmail";
    const description = kind === "TOTP" ? "securityAuthenticatorHelp" : "securityEmailFactorHelp";
    return <SecurityItem title={options.props.t(title)} description={options.props.t(description)}
        badge={options.props.t(enrolled ? "securityConnected" : "securityNotConnected")}
        {...(enrolled ? { badgeTone: "good" as const } : {})}
        action={options.props.t(enrolled ? "securityManage" : "securityConnect")}
        active={options.selected === selected} onAction={() => { options.select(selected); }} />;
}

function SecurityDetail(options: SecurityPanelProps & {
    readonly setGoogleLinked: (linked: boolean) => void;
    readonly sessions: AuthSession[];
    readonly onFactorChanged: () => Promise<void>;
}) {
    const { props, selected, factors, googleAvailable, googleLinked, setGoogleLinked, sessions, onFactorChanged } = options;
    return <Stack gap="stack" as="aside" className={styles.securityDetail ?? ""} aria-label={props.t("securitySelectedAction")}>
        {selected === "totp" && <FactorManagementSection t={props.t} enrolled={factors.enrolled.includes("TOTP")} onFactorChanged={onFactorChanged} />}
        {selected === "password" && <PasswordPanel props={props} />}
        {selected === "google" && googleAvailable && <GoogleAccountSection t={props.t} linked={googleLinked} onLinkedChange={setGoogleLinked} />}
        {selected === "email" && <EmailMfaPanel props={props} enrolled={factors.enrolled.includes("EMAIL_OTP")} onFactorChanged={onFactorChanged} />}
        {selected === "recovery" && <RecoveryCodesPanel props={props} onFactorChanged={onFactorChanged} />}
        {selected === "sessions" && <SessionsPanel props={props} sessions={sessions} />}
    </Stack>;
}

function SecurityItem({ title, description, badge, badgeTone, action, active, onAction }: {
    title: string; description: string; badge?: string; badgeTone?: "good" | undefined;
    action?: string; active?: boolean; onAction?: () => void;
}) {
    return <SettingsItem label={title} description={description} control={
        <Row gap="inline-tight" className={styles.securityItemActions ?? ""}>
            {badge && <Text variant="small" className={(badgeTone === "good" ? styles.securityBadgeGood : styles.securityBadge) ?? ""}>{badge}</Text>}
            {action && <Button type="button" tone="ghost" size="sm" className={styles.securityAction ?? ""}
                {...(active === undefined ? {} : { "aria-pressed": active })}
                {...(onAction ? { onClick: onAction } : {})}>{action}</Button>}
        </Row>
    } />;
}

function PasswordPanel({ props }: { props: AuthStepProps }) {
    const { t } = props;
    const { actions } = useToast();
    return <Stack gap="stack" as="section" aria-labelledby="password-panel-title">
        <Text as="h2" variant="title2" id="password-panel-title">{t("changePassword")}</Text>
        <Text variant="body" color="muted">{t("securityPasswordChangeHelp")}</Text>
        <AuthenticationActionForm action="CHANGE_PRIMARY_CREDENTIAL" t={t} submitLabel={t("changePasswordAction")}
            onAuthorized={async (proof, form) => {
                await authClient.postWithHeaders("/account/password", { newPassword: formText(form, "newPassword") },
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
    useOptionalSettingsUnsavedChanges(Boolean(challengeId));
    async function confirmEmail(event: SubmitEvent<HTMLFormElement>) {
        event.preventDefault();
        setConfirming(true);
        try {
            await authClient.post("/mfa/email/confirm", {
                challengeId, code: formText(new FormData(event.currentTarget), "emailMfaCode"),
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
            <Stack as="ul" gap="inline-tight" className={styles.securityCodes ?? ""}>
                {codes.map(code => <Stack as="li" gap="inline-tight" key={code}>
                    <Text as="code" variant="small">{code}</Text>
                </Stack>)}
            </Stack>
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
            <Stack as="ul" gap="inline-tight" className={styles.securitySessions ?? ""}>{sessions.map(session => <Row as="li" gap="inline" key={session.id}>
                <Stack gap="inline-tight">
                    <Text as="strong" variant="bodyStrong">{session.device}</Text>
                    <Text variant="small" color="muted">{props.t("lastUsed", { date: formatDate(session.lastUsedAt) })}</Text>
                </Stack>
                <Button tone="ghost" size="sm" type="button" onClick={() => { props.revokeSession(session.id); }}>
                    {props.t("revoke")}
                </Button>
            </Row>)}</Stack>}
    </Stack>;
}

function formatDate(value: string) {
    return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function formText(form: FormData, name: string): string {
    const value = form.get(name);
    return typeof value === "string" ? value : "";
}
