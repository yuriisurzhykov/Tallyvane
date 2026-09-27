import { Button } from "frontend-shared/ui/button";
import { Field } from "frontend-shared/ui/field";
import { Form } from "frontend-shared/ui/form";
import { Input } from "frontend-shared/ui/input";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { useState } from "react";
import type { AdminFactor } from "@/features/admin-login";
import type { AdminLoginController } from "../model/useAdminLoginController";

const factorLabels: Record<AdminFactor, "methodTotp" | "methodEmail"> = {
    TOTP: "methodTotp",
    EMAIL_OTP: "methodEmail",
};

export function AdminMfaPanel({ controller }: { readonly controller: AdminLoginController }) {
    const { state, t } = controller;
    const [showAlternatives, setShowAlternatives] = useState(false);
    const alternatives = state.availableMethods.filter(method => method !== state.factor);
    return <Stack gap="stack">
        <Stack gap="inline-tight">
            <Text as="h1" variant="title1">{t("verificationTitle")}</Text>
            <Text variant="body" color="muted">{t("verificationDescription")}</Text>
        </Stack>
        {state.notice && <Text variant="body" role="status">{state.notice}</Text>}
        {state.error && <Text variant="body" role="alert" tone="danger">{state.error}</Text>}
        <Text variant="bodyStrong">{t(factorLabels[state.factor])}</Text>
        {alternatives.length > 0 && <Button type="button" tone="ghost" onClick={() => { setShowAlternatives(current => !current); }}>
            {t(showAlternatives ? "hideOtherMethods" : "signInAnotherWay")}
        </Button>}
        {showAlternatives && <Stack gap="inline-tight">
            {alternatives.map(method => <Button
                key={method}
                type="button"
                tone="neutral"
                size="sm"
                onClick={() => { controller.setFactor(method); setShowAlternatives(false); }}
            >{t(factorLabels[method])}</Button>)}
        </Stack>}
        <Form onSubmit={controller.submitMfa} onChange={controller.clearError}>
            {!(state.factor === "EMAIL_OTP" && !state.emailChallengeId) && <Field label={state.factor === "TOTP" ? t("methodTotp") : t("verificationCode")} required>
                <Input
                    name="code"
                    autoComplete="one-time-code"
                    inputMode="numeric"
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
