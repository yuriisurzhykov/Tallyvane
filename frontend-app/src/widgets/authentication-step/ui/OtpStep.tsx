import { Button } from "frontend-shared/ui/button";
import { Form } from "frontend-shared/ui/form";
import { Input } from "frontend-shared/ui/input";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import type { AuthStepProps } from "../model/AuthStepProps";
import { AuthField } from "./AuthField";
import styles from "./auth-step.module.css";

export function OtpStep({ props }: { props: AuthStepProps }) {
    if (props.registrationPending) return <RegistrationVerificationStep props={props} />;
    if (!props.otpPurpose) return <Text variant="body" color="muted" role="status">{props.t("preparingVerification")}</Text>;
    if (props.otpPurpose === "login") return <EmailCodeSignInStep props={props} />;
    return <PasswordRecoveryStep props={props} />;
}

export function PasswordRecoveryStep({ props }: { props: AuthStepProps }) {
    const { t } = props;
    return (
        <Form className={styles.form ?? ""} onSubmit={props.submit} onChange={props.clearFieldErrors}>
            <AuthField name="email" label={t("email")} control={<Input type="email" value={props.email} onChange={event => { props.setEmail(event.target.value); }} autoComplete="email" required disabled={props.passwordResetRequested} />} errors={props.fieldErrors} />
            {props.passwordResetRequested && <ResetPasswordFields props={props} />}
            <Button tone="primary" className={styles.full ?? ""} type="submit" loading={props.busy}>
                {props.passwordResetRequested ? t("resetPassword") : t("sendResetCode")}
            </Button>
        </Form>
    );
}

function RegistrationVerificationStep({ props }: { props: AuthStepProps }) {
    const { t } = props;
    return (
        <Form className={styles.form ?? ""} onSubmit={props.submit} onChange={props.clearFieldErrors}>
            <Text variant="body" color="muted">{t("registrationCodeSent", { email: props.email })}</Text>
            <AuthField name="code" label={t("code")} control={<Input className={styles.codeInput ?? ""} value={props.code} onChange={event => { props.setCode(event.target.value); }} inputMode="numeric" autoComplete="one-time-code" maxLength={6} required />} errors={props.fieldErrors} />
            <Button tone="primary" className={styles.full ?? ""} type="submit" loading={props.busy} disabled={!props.registrationChallengeReady}>{t("verifyEmail")}</Button>
            <Button tone="ghost" size="sm" type="button" onClick={props.resendRegistrationCode} disabled={props.busy || props.registrationResendSeconds > 0}>
                {props.registrationResendSeconds > 0 ? t("resendCodeIn", { seconds: props.registrationResendSeconds }) : t("resendVerificationCode")}
            </Button>
        </Form>
    );
}

function EmailCodeSignInStep({ props }: { props: AuthStepProps }) {
    const { t } = props;
    return (
        <Form className={styles.form ?? ""} onSubmit={props.submit} onChange={props.clearFieldErrors}>
            <AuthField name="email" label={t("email")} control={<Input type="email" value={props.email} onChange={event => { props.setEmail(event.target.value); }} autoComplete="email" required disabled={props.emailCodeRequested} />} errors={props.fieldErrors} />
            {props.emailCodeRequested && <Text variant="body" color="muted">{t("registrationCodeSent", { email: props.email })}</Text>}
            {props.emailCodeRequested && <AuthField name="code" label={t("code")} control={<Input className={styles.codeInput ?? ""} value={props.code} onChange={event => { props.setCode(event.target.value); }} inputMode="numeric" autoComplete="one-time-code" maxLength={6} required />} errors={props.fieldErrors} />}
            <Button tone="primary" className={styles.full ?? ""} type="submit" loading={props.busy}>
                {props.emailCodeRequested ? t("verifyAndContinue") : t("emailCode")}
            </Button>
            {props.emailCodeRequested && <Button tone="ghost" size="sm" type="button" onClick={() => { window.location.reload(); }}>{t("usePassword")}</Button>}
        </Form>
    );
}

function ResetPasswordFields({ props }: { props: AuthStepProps }) {
    const { t } = props;
    return (
        <Stack gap="stack">
            <Text variant="body" color="muted">{t("resetCodeNeutral")}</Text>
            <AuthField name="code" label={t("code")} control={<Input className={styles.codeInput ?? ""} value={props.code} onChange={event => { props.setCode(event.target.value); }} inputMode="numeric" autoComplete="one-time-code" maxLength={6} required />} errors={props.fieldErrors} />
            <AuthField name="newPassword" label={t("newPassword")} control={<Input type="password" autoComplete="new-password" minLength={15} maxLength={128} required />} errors={props.fieldErrors} help={t("passwordHelp")} />
        </Stack>
    );
}
