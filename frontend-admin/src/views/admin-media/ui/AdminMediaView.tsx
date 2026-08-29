import { AppShell } from "frontend-shared/ui/app-shell";
import { Grid } from "frontend-shared/ui/grid";
import { Panel } from "frontend-shared/ui/panel";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import { adminNavItems } from "@/app/navigation";

interface MediaItem {
    readonly id: string;
    readonly fileName: string;
    readonly usedOn: number;
}

/** Static for now — no media API exists yet to list real uploads from. See `README.md`'s dated entry. */
const MOCK_MEDIA: MediaItem[] = [
    { id: "1", fileName: "hero-screenshot.png", usedOn: 1 },
    { id: "2", fileName: "og-image.png", usedOn: 3 },
    { id: "3", fileName: "changelog-icon.svg", usedOn: 1 },
    { id: "4", fileName: "team-photo.jpg", usedOn: 0 },
];

/**
 * Wired to `AppShell` and a real grid on 2026-08-28, replacing the
 * `EmptyState` placeholder. Alt text and a real thumbnail preview are not
 * built yet — see `README.md`'s dated entry.
 */
export function AdminMediaView() {
    return (
        <AppShell navItems={adminNavItems("/media")} title="Media" skipLinkLabel="Skip to content">
            <Grid columns={2} gap="stack">
                {MOCK_MEDIA.map((item) => (
                    <Panel key={item.id}>
                        <Stack gap="stack-tight">
                            <Surface variant="inset" className="flex h-24 items-center justify-center">
                                <Text variant="caption" color="muted">
                                    preview
                                </Text>
                            </Surface>
                            <Text variant="small" color="primary">
                                {item.fileName}
                            </Text>
                            <Text variant="caption" color="muted">
                                {item.usedOn === 0 ? "Unused" : `Used on ${String(item.usedOn)} page${item.usedOn === 1 ? "" : "s"}`}
                            </Text>
                        </Stack>
                    </Panel>
                ))}
            </Grid>
        </AppShell>
    );
}
