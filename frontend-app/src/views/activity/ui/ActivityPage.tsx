import { AppShell } from "frontend-shared/ui/app-shell";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "@/shared/i18n";
import { AccountMenu } from "@/widgets/account-menu";
import { ActivityList } from "@/widgets/activity-list";

const NAV_ITEMS = [{ label: "Today", href: "/today", isActive: false }];

/**
 * What has happened to the account's security: sign-ins and changes to the second factor. It lives under
 * `/settings` next to Devices and Security, reached from the account menu (ADR-091, ADR-096).
 */
export function ActivityPage() {
    const t = useStrings("activity");
    return (
        <AppShell navItems={NAV_ITEMS} title={t("page")} skipLinkLabel="Skip to content" actions={<AccountMenu />}>
            <Stack gap="group-gap">
                <Text variant="body" color="secondary">{t("lead")}</Text>
                <ActivityList />
            </Stack>
        </AppShell>
    );
}
