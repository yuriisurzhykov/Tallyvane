import { Button } from "frontend-shared/ui/button";
import { Field } from "frontend-shared/ui/field";
import { Form } from "frontend-shared/ui/form";
import { Input } from "frontend-shared/ui/input";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import type { AdminLoginController } from "../model/useAdminLoginController";

export function AdminEnrollmentPanel({ controller }: { readonly controller: AdminLoginController }) {
    const { state, t } = controller;
    return <Stack gap="stack">
        <Stack gap="inline-tight">
            <Text as="h1" variant="title1">{t("enrollmentTitle")}</Text>
            <Text variant="body" color="muted">{t("enrollmentDescription")}</Text>
        </Stack>
        {state.error && <Text variant="body" role="alert" tone="danger">{state.error}</Text>}
        {!state.otpauthUri && <Text variant="body" role="status">{t("preparingEnrollment")}</Text>}
        {state.otpauthUri && <EnrollmentSecret uri={state.otpauthUri} t={t} />}
        <Form onSubmit={controller.submitEnrollment}>
            <Field label={t("enrollmentCode")} required>
                <Input
                    name="code"
                    autoComplete="one-time-code"
                    inputMode="numeric"
                    value={state.code}
                    onChange={event => { controller.setCode(event.target.value); }}
                    required
                    disabled={state.busy || !state.otpauthUri}
                />
            </Field>
            <Button tone="primary" type="submit" loading={state.busy} disabled={!state.otpauthUri}>{t("confirmEnrollment")}</Button>
        </Form>
    </Stack>;
}

function EnrollmentSecret({ uri, t }: { readonly uri: string; readonly t: AdminLoginController["t"] }) {
    return <Stack gap="inline-tight">
        <Text variant="small" color="muted">{t("manualSetupKey")}</Text>
        <Text as="code" variant="small" className="break-all">{readSecret(uri)}</Text>
    </Stack>;
}

function readSecret(value: string): string {
    try {
        return new URL(value).searchParams.get("secret") ?? value;
    } catch {
        return value;
    }
}
