import { Button } from "frontend-shared/ui/button";
import { Collapsible } from "frontend-shared/ui/collapsible";
import { Form } from "frontend-shared/ui/form";
import { Input } from "frontend-shared/ui/input";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import type { AuthStepProps } from "../model/AuthStepProps";
import type { AuthPageKind } from "../../../features/authentication/model/AuthPageKind";
import { AuthField } from "./AuthField";
import styles from "./auth-step.module.css";

export function CredentialStep({ kind, props }: { kind: Extract<AuthPageKind, "login" | "register">; props: AuthStepProps }) {
    const { t } = props;
    return (
        <Stack gap="stack" className={styles.form ?? ""}>
            {props.primaryMethods.includes("GOOGLE") && <GoogleProviderAction props={props} />}
            {props.primaryMethods.includes("PASSWORD") && (
                kind === "register" ? <RegistrationForm props={props} /> : <PasswordSignInForm props={props} />
            )}
            {kind === "login" && props.primaryMethods.includes("EMAIL_SIGN_IN_CODE") &&
                <Text variant="small" color="muted">{t("emailCodeAvailableHelp")}</Text>}
            {props.primaryMethods.length === 0 &&
                <Text variant="body" tone="danger" role="alert">{t("signInOptionsUnavailable")}</Text>}
        </Stack>
    );
}

function RegistrationForm({ props }: { props: AuthStepProps }) {
    const { t } = props;
    return (
        <Collapsible.Root className={styles.emailDisclosure ?? ""}>
            <Collapsible.Trigger className={styles.disclosureSummary ?? ""}>
                <Stack gap="inline-tight">
                    <Text variant="bodyStrong" className={styles.disclosureTitle ?? ""}>{t("emailDisclosure")}</Text>
                    <Text variant="small" color="muted">{t("emailDisclosureHelp")}</Text>
                </Stack>
            </Collapsible.Trigger>
            <Collapsible.Panel>
                <Form className={styles.form ?? ""} onSubmit={props.submit} onChange={props.clearFieldErrors}>
                    <AuthField name="name" label={t("name")} control={<Input autoComplete="name" />} errors={props.fieldErrors} />
                    <AuthField name="email" label={t("email")} control={<Input type="email" autoComplete="email" required />} errors={props.fieldErrors} />
                    <AuthField name="password" label={t("password")} control={<Input type={props.showPassword ? "text" : "password"} autoComplete="new-password" minLength={15} maxLength={128} required />} errors={props.fieldErrors} help={t("passwordHelp")} />
                    <PasswordVisibility props={props} />
                    <Button tone="primary" className={styles.full ?? ""} type="submit" loading={props.busy}>{t("createAccountAction")}</Button>
                </Form>
            </Collapsible.Panel>
        </Collapsible.Root>
    );
}

function PasswordSignInForm({ props }: { props: AuthStepProps }) {
    const { t } = props;
    return (
        <>
            <Text variant="small" className={styles.divider ?? ""}>{t("divider")}</Text>
            <Form className={styles.form ?? ""} onSubmit={props.submit} onChange={props.clearFieldErrors}>
                <AuthField name="email" label={t("email")} control={<Input type="email" autoComplete="email" required />} errors={props.fieldErrors} />
                <AuthField name="password" label={t("password")} control={<Input type={props.showPassword ? "text" : "password"} autoComplete="current-password" minLength={15} maxLength={128} required />} errors={props.fieldErrors} />
                <PasswordVisibility props={props} />
                <Button tone="primary" className={styles.full ?? ""} type="submit" loading={props.busy}>{t("signIn")}</Button>
            </Form>
        </>
    );
}

function GoogleProviderAction({ props }: { props: AuthStepProps }) {
    const { t } = props;
    const content = <><Text variant="small" className={styles.methodIcon ?? ""} aria-hidden="true">G</Text>{t("continueWithGoogle")}</>;
    return <Button tone="neutral" className={styles.provider ?? ""} type="button"
        onClick={props.startGoogleSignIn}>{content}</Button>;
}

function PasswordVisibility({ props }: { props: AuthStepProps }) {
    const { t, showPassword, setShowPassword } = props;
    return (
        <Button tone="ghost" size="sm" className={styles.quiet ?? ""} type="button" aria-pressed={showPassword}
                onClick={() => { setShowPassword(!showPassword); }}>
            {showPassword ? t("hidePassword") : t("showPassword")}
        </Button>
    );
}
