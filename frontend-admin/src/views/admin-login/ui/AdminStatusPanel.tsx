import { Button } from "frontend-shared/ui/button";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import type { AdminLoginController } from "../model/useAdminLoginController";

export function AdminStatusPanel({ controller }: { readonly controller: AdminLoginController }) {
    const { state, t } = controller;
    if (state.screen === "checking") return <Text variant="body" role="status">{t("checkingSession")}</Text>;
    if (state.screen === "denied") return <Stack gap="stack-tight" role="alert">
        <Text variant="title2">{t("accessDeniedTitle")}</Text>
        <Text variant="body" tone="danger">{state.error || t("accessDenied")}</Text>
        <Button tone="neutral" loading={state.busy} onClick={controller.signOut}>{t("tryAnotherAccount")}</Button>
    </Stack>;
    return <Stack gap="stack-tight" role="alert">
        <Text variant="title2">{t("title")}</Text>
        <Text variant="body" tone="danger">{state.error}</Text>
        <Button tone="primary" onClick={controller.retry}>{t("retry")}</Button>
    </Stack>;
}
