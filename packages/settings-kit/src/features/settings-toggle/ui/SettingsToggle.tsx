"use client";

import { SettingsItem } from "settings-kit/entities/settings-item";
import { Button } from "frontend-shared/ui/button";
import { Stack } from "frontend-shared/ui/stack";
import { Switch } from "frontend-shared/ui/switch";
import { Text } from "frontend-shared/ui/text";
import { useImmediateSetting, type UseImmediateSettingOptions } from "../model/useImmediateSetting";

export interface SettingsToggleProps extends UseImmediateSettingOptions {
    readonly label: string;
    readonly description?: string;
    readonly statusLabels: {
        readonly saving: string;
        readonly saved: string;
        readonly error: string;
        readonly retry: string;
    };
}

/** An immediately applied setting with optimistic display, rollback, and retry feedback. */
export function SettingsToggle({ label, description, statusLabels, ...options }: SettingsToggleProps) {
    const setting = useImmediateSetting(options);
    const status = setting.status === "saving"
        ? <Text variant="caption" color="muted">{statusLabels.saving}</Text>
        : setting.status === "saved"
            ? <Text variant="caption" tone="success">{statusLabels.saved}</Text>
            : setting.status === "error"
                ? (
                    <Stack gap="inline-tight" className="items-end sm:flex-row">
                        <Text variant="caption" tone="danger">{statusLabels.error}</Text>
                        <Button tone="danger" size="sm" type="button" onClick={setting.retry}>
                            {statusLabels.retry}
                        </Button>
                    </Stack>
                )
                : null;

    return (
        <SettingsItem
            label={label}
            {...(description ? { description } : {})}
            control={(
                <Switch
                    aria-label={label}
                    checked={setting.value}
                    onCheckedChange={setting.setValue}
                />
            )}
            {...(status ? { status } : {})}
        />
    );
}
