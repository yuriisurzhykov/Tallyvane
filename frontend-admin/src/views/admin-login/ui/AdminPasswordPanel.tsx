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
        <Form onSubmit={controller.submitPassword} onChange={controller.clearError}>
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
        </Form>
    </Stack>;
}
