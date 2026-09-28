"use client";

import { useSettingsUnsavedChanges } from "settings-kit/features/settings-navigation";
import { Button } from "frontend-shared/ui/button";
import { Row } from "frontend-shared/ui/row";
import { Text } from "frontend-shared/ui/text";

export interface SettingsFormActionsProps {
    readonly isDirty: boolean;
    readonly isSaving: boolean;
    readonly canSave?: boolean;
    /** Use a direct action when the settings module is not wrapped in a form. */
    readonly onSave?: () => void;
    readonly onCancel: () => void;
    readonly status?: {
        readonly tone: "danger" | "success";
        readonly label: string;
    };
    readonly labels: {
        readonly save: string;
        readonly cancel: string;
        readonly saving: string;
    };
}

/** Explicit-save actions for a module-owned form; form values and validation remain with that module. */
export function SettingsFormActions({ isDirty, isSaving, canSave = true, onSave, onCancel, status, labels }: SettingsFormActionsProps) {
    useSettingsUnsavedChanges(isDirty);

    return (
        <Row gap="inline" className="justify-end">
            {isSaving ? <Text variant="small" color="muted">{labels.saving}</Text> : null}
            {status ? <Text variant="small" tone={status.tone}>{status.label}</Text> : null}
            <Button tone="neutral" type="button" disabled={!isDirty || isSaving} onClick={onCancel}>
                {labels.cancel}
            </Button>
            <Button
                tone="primary"
                type={onSave ? "button" : "submit"}
                disabled={!isDirty || !canSave}
                loading={isSaving}
                {...(onSave ? { onClick: onSave } : {})}
            >
                {labels.save}
            </Button>
        </Row>
    );
}
