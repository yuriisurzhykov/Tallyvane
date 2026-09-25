import { Button } from "frontend-shared/ui/button";
import { Form } from "frontend-shared/ui/form";
import { Input } from "frontend-shared/ui/input";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import type { AuthSession, AuthStepProps } from "../model/AuthStepProps";
import { FactorManagementSection } from "./FactorManagementSection";
import { AuthField } from "./AuthField";
import { GoogleAccountSection } from "./GoogleAccountSection";
import styles from "./auth-step.module.css";

export function SecuritySettingsStep({ props }: { props: AuthStepProps }) {
    return (
        <Stack gap="section-gap">
            <PasswordChangeSection props={props} />
            <GoogleAccountSection t={props.t} />
            <FactorManagementSection t={props.t} emailMfaEnrollmentChallengeId={props.emailMfaEnrollmentChallengeId} />
            <EmailMfaSection props={props} />
            <RecoveryCodesSection props={props} />
            <ActiveSessionsSection props={props} />
        </Stack>
    );
}

function PasswordChangeSection({ props }: { props: AuthStepProps }) {
    const { t } = props;
    return (
        <Form className={styles.form ?? ""} onSubmit={props.submit} onChange={props.clearFieldErrors}>
            <AuthField name="currentPassword" label={t("currentPassword")} control={<Input type="password" autoComplete="current-password" required />} errors={props.fieldErrors} />
            <AuthField name="newPassword" label={t("newPassword")} control={<Input type="password" autoComplete="new-password" minLength={15} maxLength={128} required />} errors={props.fieldErrors} help={t("passwordHelp")} />
            <Button tone="primary" className={styles.full ?? ""} type="submit" loading={props.busy}>{t("changePasswordAction")}</Button>
        </Form>
    );
}

function EmailMfaSection({ props }: { props: AuthStepProps }) {
    const { t } = props;
    const enrolled = Boolean(props.emailMfaEnrollmentChallengeId);
    return (
        <Stack as="section" gap="stack" className={styles.form ?? ""} aria-labelledby="email-mfa-heading">
            <Text variant="title2" as="h2" id="email-mfa-heading">{t("emailMfaTitle")}</Text>
            <Text variant="body" color="muted">{t("emailMfaDescription")}</Text>
            <Form onSubmit={props.enrollEmailMfa}>
                {!enrolled && <AuthField name="emailMfaPassword" label={t("emailMfaPassword")} control={<Input type="password" autoComplete="current-password" required />} errors={props.fieldErrors} />}
                {enrolled && <Text variant="body" color="muted" role="status">{t("emailMfaCodeSentHelp")}</Text>}
                {enrolled && <AuthField name="emailMfaCode" label={t("emailMfaCode")} control={<Input className={styles.codeInput ?? ""} inputMode="numeric" autoComplete="one-time-code" maxLength={6} required />} errors={props.fieldErrors} />}
                <Button tone="primary" className={styles.full ?? ""} type="submit" loading={props.busy}>
                    {enrolled ? t("emailMfaConfirm") : t("emailMfaEnable")}
                </Button>
            </Form>
        </Stack>
    );
}

function RecoveryCodesSection({ props }: { props: AuthStepProps }) {
    const { t } = props;
    return (
        <Stack as="section" gap="stack" className={styles.form ?? ""} aria-labelledby="recovery-codes-heading">
            <Text variant="title2" as="h2" id="recovery-codes-heading">{t("recoveryCodesTitle")}</Text>
            <Text variant="body" color="muted">{t("backupWarning")}</Text>
            <Form onSubmit={props.issueRecoveryCodes}>
                <AuthField name="backupCurrentPassword" label={t("confirmPassword")} control={<Input type="password" autoComplete="current-password" required />} errors={props.fieldErrors} />
                <Button tone="primary" className={styles.full ?? ""} type="submit" loading={props.busy}>{t("backupGenerate")}</Button>
            </Form>
            {props.recoveryCodes.length > 0 && <RecoveryCodesList props={props} />}
        </Stack>
    );
}

function RecoveryCodesList({ props }: { props: AuthStepProps }) {
    return (
        <Stack gap="stack-tight" role="status">
            <Text variant="small" color="muted">{props.t("recoveryCodesOneTime")}</Text>
            <Stack as="ul" gap="inline-tight" aria-label={props.t("recoveryCodesTitle")}>
                {props.recoveryCodes.map(code => <Stack as="li" gap="inline-tight" key={code}>
                    <Text variant="small" as="code">{code}</Text>
                </Stack>)}
            </Stack>
            <Button tone="ghost" size="sm" type="button" onClick={props.clearRecoveryCodes}>{props.t("backupCodesSaved")}</Button>
        </Stack>
    );
}

function ActiveSessionsSection({ props }: { props: AuthStepProps }) {
    const sessions = props.sessions.filter(session => !session.revokedAt);
    return (
        <Stack as="section" gap="stack" className={styles.form ?? ""} aria-labelledby="sessions-heading">
            <Text variant="title2" as="h2" id="sessions-heading">{props.t("sessions")}</Text>
            {sessions.length === 0 ? <Text variant="body" color="muted">{props.t("noSessions")}</Text> : (
                <Stack as="ul" gap="inline-tight">
                    {sessions.map(session => <SessionItem key={session.id} session={session} props={props} />)}
                </Stack>
            )}
        </Stack>
    );
}

function SessionItem({ session, props }: { session: AuthSession; props: AuthStepProps }) {
    return (
        <Row as="li" gap="inline" className={styles.row ?? ""}>
            <Text variant="small">{session.device}</Text>
            <Text variant="small" color="muted">{props.t("lastUsed", { date: formatDate(session.lastUsedAt) })}</Text>
            <Button tone="ghost" size="sm" type="button" onClick={() => { props.revokeSession(session.id); }}>
                {props.t("revoke")}
            </Button>
        </Row>
    );
}

function formatDate(value: string) {
    return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}
