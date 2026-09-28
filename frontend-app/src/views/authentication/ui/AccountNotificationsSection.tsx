"use client";

import { useEffect, useState } from "react";
import { authClient } from "../../../features/authentication/api/client";
import type { useAuthStrings } from "../../../features/authentication/model/strings";
import { Text } from "frontend-shared/ui/text";
import { Button } from "frontend-shared/ui/button";
import { SettingsToggle } from "settings-kit/features/settings-toggle";
import { SettingsGroup } from "settings-kit/widgets/settings-group";

type Translate = ReturnType<typeof useAuthStrings>;

interface Preferences {
    readonly securityEmailsEnabled: boolean
}

export function AccountNotificationsSection({ t }: { readonly t: Translate }) {
    const [value, setValue] = useState<boolean | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [retry, setRetry] = useState(0);
    useEffect(() => {
        let active = true;
        void authClient.get<Preferences>("/account/notifications").then(preferences => {
            if (active) {
                setValue(preferences.securityEmailsEnabled);
                setError(null);
            }
        }).catch(() => {
            if (active) setError(t("settingsLoadFailed"));
        });
        return () => {
            active = false;
        };
    }, [t, retry]);
    if (value === null) return error ? <SettingsGroup title={ t("settingsNotifications") }>
        <Text variant="body" role="alert" tone="danger">{ error }</Text>
        <Button type="button" tone="neutral" onClick={ () => {
            setError(null);
            setRetry(value => value + 1);
        } }>
            { t("settingsRetry") }
        </Button>
    </SettingsGroup> : <Text variant="body" role="status">{ t("checkingSession") }</Text>;
    return <SettingsGroup title={ t("settingsNotifications") }>
        <SettingsToggle label={ t("settingsSecurityEmails") } description={ t("settingsSecurityEmailsHelp") }
                        value={ value } onSave={ async enabled => {
            const result = await authClient.put<Preferences>("/account/notifications", { securityEmailsEnabled: enabled });
            setValue(result.securityEmailsEnabled);
        } }
                        statusLabels={ {
                            saving: t("settingsSaving"),
                            saved: t("settingsSaved"),
                            error: t("settingsSaveFailed"),
                            retry: t("settingsRetry")
                        } }/>
    </SettingsGroup>;
}
