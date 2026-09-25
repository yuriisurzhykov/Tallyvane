import { Button } from "frontend-shared/ui/button";
import { Field } from "frontend-shared/ui/field";
import { Form } from "frontend-shared/ui/form";
import { Input } from "frontend-shared/ui/input";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import type { AdminFactor } from "@/features/admin-login";
import type { AdminLoginController } from "../model/useAdminLoginController";

const factorLabels: Record<AdminFactor, "methodTotp" | "methodEmail" | "methodBackup"> = {
    TOTP: "methodTotp",
    EMAIL_OTP: "methodEmail",
    BACKUP_CODE: "methodBackup",
};

export function AdminMfaPanel({ controller }: { readonly controller: AdminLoginController }) {
    const { state, t } = controller;
    const methods = state.availableMethods.length ? state.availableMethods : ["TOTP" as const];
    return <Stack gap="stack">
        <Stack gap="inline-tight">
            <Text as="h1" variant="title1">{t("verificationTitle")}</Text>
            <Text variant="body" color="muted">{t("verificationDescription")}</Text>
        </Stack>
        {state.notice && <Text variant="body" role="status">{state.notice}</Text>}
        {state.error && <Text variant="body" role="alert" tone="danger">{state.error}</Text>}
        <Row gap="inline" className="flex-wrap">
            {methods.map(method => <Button
                key={method}
                type="button"
                tone={state.factor === method ? "primary" : "neutral"}
                size="sm"
                aria-pressed={state.factor === method}
                onClick={() => { controller.setFactor(method); }}
            >{t(factorLabels[method])}</Button>)}
        </Row>
        <Form onSubmit={controller.submitMfa} onChange={controller.clearError}>
            {!(state.factor === "EMAIL_OTP" && !state.emailChallengeId) && <Field label={state.factor === "TOTP" ? t("methodTotp") : t("verificationCode")} required>
                <Input
                    name="code"
                    autoComplete="one-time-code"
                    inputMode={state.factor === "BACKUP_CODE" ? "text" : "numeric"}
                    value={state.code}
                    onChange={event => { controller.setCode(event.target.value); }}
                    required
                    disabled={state.busy}
                />
            </Field>}
            <Button tone="primary" type="submit" loading={state.busy}>
                {state.factor === "EMAIL_OTP" && !state.emailChallengeId ? t("sendEmailCode") : t("verifyAndContinue")}
            </Button>
        </Form>
        <Button tone="ghost" disabled={state.busy} onClick={controller.backToPassword}>{t("backToSignIn")}</Button>
    </Stack>;
}
