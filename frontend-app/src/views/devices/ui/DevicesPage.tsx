import { AppShell } from "frontend-shared/ui/app-shell";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { AccountMenu } from "@/widgets/account-menu";
import { DeviceList } from "@/widgets/device-list";

const NAV_ITEMS = [{ label: "Today", href: "/today", isActive: false }];

/**
 * The devices a person is signed in on. It lives under `/settings` because the settings page that will hold
 * it (second factor, security journal) does not exist yet; no nav item leads here, the account menu does.
 */
export function DevicesPage() {
    return (
        <AppShell navItems={NAV_ITEMS} title="Devices" skipLinkLabel="Skip to content" actions={<AccountMenu />}>
            <Stack gap="group-gap">
                <Text variant="body" color="secondary">
                    Where you are signed in. Sign out on any you no longer use.
                </Text>
                <DeviceList />
            </Stack>
        </AppShell>
    );
}
