import type { ReactNode } from "react";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";

export interface SettingsGroupProps {
    readonly title: string;
    readonly description?: string;
    readonly children: ReactNode;
}

/** A titled group for related settings items. */
export function SettingsGroup({ title, description, children }: SettingsGroupProps) {
    return (
        <Stack as="section" gap="stack-tight" aria-label={title}>
            <Stack gap="inline-tight">
                <Text variant="title3" as="h2">{title}</Text>
                {description ? <Text variant="small" color="muted">{description}</Text> : null}
            </Stack>
            <Stack gap="stack-tight">
                {children}
            </Stack>
        </Stack>
    );
}
