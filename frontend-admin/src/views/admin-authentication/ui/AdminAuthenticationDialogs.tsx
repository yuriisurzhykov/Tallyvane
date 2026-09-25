import { Button } from "frontend-shared/ui/button";
import { Drawer } from "frontend-shared/ui/drawer";
import { Field } from "frontend-shared/ui/field";
import { Form } from "frontend-shared/ui/form";
import { Input } from "frontend-shared/ui/input";
import { Panel } from "frontend-shared/ui/panel";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import type { useAdminAuthenticationStrings } from "@/app/i18n";

type Translate = ReturnType<typeof useAdminAuthenticationStrings>;

export interface ResetFactorPanelProps {
    readonly t: Translate;
    readonly email: string;
    readonly busy: boolean;
    readonly onEmailChange: (email: string) => void;
    readonly onRequest: () => void;
}

export function ResetFactorPanel({ t, email, busy, onEmailChange, onRequest }: ResetFactorPanelProps) {
    return <Panel header={ <Text variant="bodyStrong">{ t("resetTitle") }</Text> }>
        <Form onSubmit={ event => { event.preventDefault(); onRequest(); } }>
            <Text variant="body">{ t("resetHelp") }</Text>
            <Field label={ t("accountEmail") } required>
                <Input id="reset-email" name="email" type="email" required value={ email } disabled={ busy }
                       onChange={ event => { onEmailChange(event.target.value); } } />
            </Field>
            <Button tone="danger" type="submit" disabled={ busy || !email.trim() }>{ t("resetSecondFactors") }</Button>
        </Form>
    </Panel>;
}

export interface ConfirmationDrawerProps {
    readonly t: Translate;
    readonly email: string;
    readonly confirmation: "advanced" | "reset" | null;
    readonly onClose: () => void;
    readonly onConfirm: () => void;
}

export function ConfirmationDrawer({ t, email, confirmation, onClose, onConfirm }: ConfirmationDrawerProps) {
    const advanced = confirmation === "advanced";
    return <Drawer.Root open={ confirmation !== null } onOpenChange={ open => { if (!open) onClose(); } }>
        <Drawer.Popup>
            <Drawer.Title>{ advanced ? t("advancedWarningTitle") : t("resetConfirmationTitle") }</Drawer.Title>
            <Drawer.Description>{ advanced ? t("advancedWarningBody") : t("resetConfirmationBody", { email: email.trim() }) }</Drawer.Description>
            <Stack gap="inline">
                <Button tone="neutral" onClick={ onClose }>{ t("cancel") }</Button>
                <Button tone="danger" onClick={ onConfirm }>{ advanced ? t("acceptRiskSave") : t("resetAndRevoke") }</Button>
            </Stack>
        </Drawer.Popup>
    </Drawer.Root>;
}
