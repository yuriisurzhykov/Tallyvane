import { Button } from "frontend-shared/ui/button";
import { Field } from "frontend-shared/ui/field";
import { Form } from "frontend-shared/ui/form";
import { Input } from "frontend-shared/ui/input";
import { PasswordField } from "frontend-shared/ui/password-field";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import type { AdminLoginController } from "../model/useAdminLoginController";

export function AdminPasswordPanel({ controller }: { readonly controller: AdminLoginController }) {
    const { state, t } = controller;
    return <Stack gap="stack">
        <Stack gap="inline-tight">
            <Text as="h1" variant="title1">{t("title")}</Text>
            <Text variant="body" color="muted">{t("description")}</Text>
        </Stack>
        {state.notice && <Text variant="body" role="status">{state.notice}</Text>}
        {state.error && <Text variant="body" role="alert" tone="danger">{state.error}</Text>}
        {state.primaryMethods.includes("GOOGLE") && <Button tone="neutral" type="button" onClick={controller.startGoogleSignIn}>
            {t("continueWithGoogle")}
        </Button>}
        {state.signInMode === "PASSWORD" && state.primaryMethods.includes("PASSWORD") && <Form onSubmit={controller.submitPassword} onChange={controller.clearError}>
            <Field label={t("email")} required>
                <Input name="email" type="email" autoComplete="username" value={state.email} onChange={event => { controller.setEmail(event.target.value); }} required disabled={state.busy} />
            </Field>
            <Field label={t("password")} required>
                <PasswordField
                    name="password"
                    autoComplete="current-password"
                    value={state.password}
                    onChange={event => { controller.setPassword(event.target.value); }}
                    showPasswordLabel={t("showPassword")}
                    hidePasswordLabel={t("hidePassword")}
                    required
                    disabled={state.busy}
                />
            </Field>
            <Button tone="primary" type="submit" loading={state.busy}>{t("signIn")}</Button>
        </Form>}
        {state.signInMode === "EMAIL_SIGN_IN_CODE" && <Form onSubmit={controller.submitEmailCode} onChange={controller.clearError}>
            <Field label={t("email")} required>
                <Input name="email" type="email" autoComplete="email" value={state.email}
                    onChange={event => { controller.setEmail(event.target.value); }} required
                    disabled={state.busy || Boolean(state.emailSignInChallengeId)} />
            </Field>
            {state.emailSignInChallengeId && <Field label={t("verificationCode")} required>
                <Input name="code" autoComplete="one-time-code" inputMode="numeric" value={state.code}
                    onChange={event => { controller.setCode(event.target.value); }} required disabled={state.busy} />
            </Field>}
            <Button tone="primary" type="submit" loading={state.busy}>
                {t(state.emailSignInChallengeId ? "verifyAndContinue" : "sendEmailCode")}
            </Button>
        </Form>}
        {state.primaryMethods.includes("EMAIL_SIGN_IN_CODE") && state.primaryMethods.includes("PASSWORD") && <Button tone="ghost" type="button"
            onClick={() => { controller.setSignInMode(state.signInMode === "PASSWORD" ? "EMAIL_SIGN_IN_CODE" : "PASSWORD"); }}>
            {t(state.signInMode === "PASSWORD" ? "useEmailCode" : "usePassword")}
        </Button>}
        {state.primaryMethods.length === 0 && <Text variant="body" role="alert" tone="danger">
            {t("signInOptionsUnavailable")}
        </Text>}
    </Stack>;
}
