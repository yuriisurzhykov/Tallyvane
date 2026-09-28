import type { ReactNode } from "react";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";

export interface SettingsItemProps {
    readonly label: string;
    readonly description?: string;
    readonly control: ReactNode;
    readonly status?: ReactNode;
}

/**
 * A domain-neutral label, explanation, control, and optional save status.
 * */
export function SettingsItem({ label, description, control, status }: SettingsItemProps) {
    return (
        <Row gap="group-gap"
             className="items-start justify-between border-b border-border-subtle py-stack last:border-0">
            <Stack gap="inline-tight" className="min-w-0 flex-1">
                <Text variant="bodyStrong">{ label }</Text>
                { description ? <Text variant="small" color="muted">{ description }</Text> : null }
            </Stack>
            <Stack gap="inline-tight" className="shrink-0 items-end">
                { control }
                { status }
            </Stack>
        </Row>
    );
}
