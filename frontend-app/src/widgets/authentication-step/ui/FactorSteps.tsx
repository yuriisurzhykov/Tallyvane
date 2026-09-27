import { useState } from "react";
import { Button } from "frontend-shared/ui/button";
import { Form } from "frontend-shared/ui/form";
import { Image } from "frontend-shared/ui/image";
import { Input } from "frontend-shared/ui/input";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import { useToast } from "frontend-shared/ui/toast";
import type { AuthStepProps } from "../model/AuthStepProps";
import { AuthField } from "./AuthField";
import styles from "./auth-step.module.css";

export function FactorVerificationStep({ props }: { props: AuthStepProps }) {
    const { t, factor, setFactor } = props;
    const [showAlternatives, setShowAlternatives] = useState(false);
    const alternatives = props.availableMethods.filter(method => method !== factor);
    return (
        <Form className={styles.form ?? ""} onSubmit={props.submit} onChange={props.clearFieldErrors}>
            <Text variant="bodyStrong">{factorLabel(factor, t)}</Text>
            {(factor !== "EMAIL_OTP" || props.emailMfaCodeRequested) && <AuthField
                name="code"
                label={factor === "TOTP" ? t("authenticatorCode") : t("code")}
                control={<Input className={styles.codeInput ?? ""} value={props.code} onChange={event => { props.setCode(event.target.value); }} autoComplete="one-time-code" inputMode="numeric" required />}
                errors={props.fieldErrors}
            />}
            <Button tone="primary" className={styles.full ?? ""} type="submit" loading={props.busy}>
                {factor === "EMAIL_OTP" && !props.emailMfaCodeRequested ? t("sendEmailMfaCode") : t("verifyAndContinue")}
            </Button>
            {alternatives.length > 0 && <Button type="button" tone="ghost" onClick={() => {
                setShowAlternatives(current => !current);
            }}>{t(showAlternatives ? "hideOtherMethods" : "signInAnotherWay")}</Button>}
            {showAlternatives && <Row gap="inline" className="flex-wrap">
                {alternatives.map(method => <Button key={method} type="button" tone="neutral" size="sm" onClick={() => {
                    setFactor(method);
                    setShowAlternatives(false);
                }}>{factorLabel(method, t)}</Button>)}
            </Row>}
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
    const { actions } = useToast();
    const copySecret = async () => {
        try {
            await navigator.clipboard.writeText(secret);
            actions.add({ title: t("copied"), tone: "success" });
        } catch {
            actions.add({ title: t("copyFailed"), tone: "danger" });
        }
    };

    return (
        <Stack gap="inline-tight">
            {qrCode ? <Image className={styles.qr ?? ""} src={qrCode} alt={t("authenticatorQr")} /> : <Text variant="body" role="status">{t("qrUnavailable")}</Text>}
            <Text variant="small" color="muted">{t("manualSetupKey")}</Text>
            <Text variant="small" as="code" className={styles.secret ?? ""}>{secret}</Text>
            <Button type="button" tone="neutral" onClick={() => { void copySecret(); }}>{t("copy")}</Button>
        </Stack>
    );
}

export function GoogleStep({ props }: { props: AuthStepProps }) {
    return props.primaryMethods.includes("GOOGLE") ? (
        <Button tone="neutral" className={styles.provider ?? ""} type="button" onClick={props.startGoogleSignIn}>
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
    return t("emailCodeLabel");
}
