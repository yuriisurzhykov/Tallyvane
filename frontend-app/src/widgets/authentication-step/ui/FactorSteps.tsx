import Link from "next/link";
import { Button } from "frontend-shared/ui/button";
import { Form } from "frontend-shared/ui/form";
import { Image } from "frontend-shared/ui/image";
import { Input } from "frontend-shared/ui/input";
import { Select } from "frontend-shared/ui/select";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import type { AuthStepProps } from "../model/AuthStepProps";
import { AuthField } from "./AuthField";
import styles from "./auth-step.module.css";

export function FactorVerificationStep({ props }: { props: AuthStepProps }) {
    const { t, factor, setFactor } = props;
    const methods = props.availableMethods.length ? props.availableMethods : ["TOTP"];
    return (
        <Form className={styles.form ?? ""} onSubmit={props.submit} onChange={props.clearFieldErrors}>
            <Stack gap="inline-tight" className={styles.field ?? ""}>
                <Select.Root value={factor} onValueChange={value => { if (typeof value === "string") setFactor(value); }}>
                    <Select.Label className={styles.fieldLabel ?? ""}>{t("verificationMethod")}</Select.Label>
                    <Select.Trigger id="auth-factor" name="factor" aria-invalid={Boolean(props.fieldErrors.factor)}
                                    aria-describedby={props.fieldErrors.factor ? "auth-factor-error" : undefined}>
                        <Select.Value>{factorLabel(factor, t)}</Select.Value>
                        <Select.Icon />
                    </Select.Trigger>
                    <Select.Popup>
                        {methods.map(method => <Select.Item key={method} value={method}>{factorLabel(method, t)}</Select.Item>)}
                    </Select.Popup>
                </Select.Root>
                {props.fieldErrors.factor && <Text variant="caption" tone="danger" id="auth-factor-error">{props.fieldErrors.factor}</Text>}
            </Stack>
            {(factor !== "EMAIL_OTP" || props.emailMfaCodeRequested) && <AuthField
                name="code"
                label={factor === "TOTP" ? t("authenticatorCode") : factor === "EMAIL_OTP" ? t("code") : t("backupCodeLabel")}
                control={<Input className={styles.codeInput ?? ""} value={props.code} onChange={event => { props.setCode(event.target.value); }} autoComplete="one-time-code" inputMode="numeric" required />}
                errors={props.fieldErrors}
            />}
            <Button tone="primary" className={styles.full ?? ""} type="submit" loading={props.busy}>
                {factor === "EMAIL_OTP" && !props.emailMfaCodeRequested ? t("sendEmailMfaCode") : t("verifyAndContinue")}
            </Button>
        </Form>
    );
}

export function AuthenticatorEnrollmentStep({ props }: { props: AuthStepProps }) {
    const { t } = props;
    const secret = props.payload ? new URL(props.payload).searchParams.get("secret") ?? props.payload : "";
    return (
        <Form className={styles.form ?? ""} onSubmit={props.submit} onChange={props.clearFieldErrors}>
            {props.payload && <AuthenticatorSetup qrCode={props.qrCode} secret={secret} t={t} />}
            <AuthField name="code" label={t("codeFromAuthenticator")} control={<Input className={styles.codeInput ?? ""} value={props.code} onChange={event => { props.setCode(event.target.value); }} inputMode="numeric" autoComplete="one-time-code" />} errors={props.fieldErrors} />
            <Button tone="primary" className={styles.full ?? ""} type="submit" loading={props.busy}>
                {props.payload ? t("confirmAuthenticator") : t("enrollStart")}
            </Button>
        </Form>
    );
}

function AuthenticatorSetup({ qrCode, secret, t }: { qrCode: string; secret: string; t: AuthStepProps["t"] }) {
    return (
        <Stack gap="inline-tight">
            {qrCode ? <Image className={styles.qr ?? ""} src={qrCode} alt={t("authenticatorQr")} /> : <Text variant="body" role="status">{t("qrUnavailable")}</Text>}
            <Text variant="small" color="muted">{t("manualSetupKey")}</Text>
            <Text variant="small" as="code" className={styles.secret ?? ""}>{secret}</Text>
        </Stack>
    );
}

export function GoogleStep({ props }: { props: AuthStepProps }) {
    return props.googleEnabled ? (
        <Button tone="neutral" className={styles.provider ?? ""} render={<Link href="/api/v1/auth/google/oauth/start" />}>
            {props.t("continueWithGoogle")}
        </Button>
    ) : <Text variant="body" color="muted">{props.t("googleNotConfigured")}</Text>;
}

export function PreviewStep({ props }: { props: AuthStepProps }) {
    return (
        <Surface variant="inset" className={styles.notice ?? ""}>
            <Text variant="bodyStrong">{props.t("previewOnly")}</Text>
            <Text variant="body">{props.t("previewCatalogHelp")}</Text>
        </Surface>
    );
}

function factorLabel(method: string, t: AuthStepProps["t"]) {
    if (method === "TOTP") return t("methodTotp");
    if (method === "BACKUP_CODE") return t("backupCodeLabel");
    return t("emailCodeLabel");
}
