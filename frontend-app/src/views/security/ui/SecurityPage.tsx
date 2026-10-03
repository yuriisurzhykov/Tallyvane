import { AppShell } from "frontend-shared/ui/app-shell";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "@/shared/i18n";
import { AccountMenu } from "@/widgets/account-menu";
import { SecondFactorCard } from "@/widgets/second-factor-card";

const NAV_ITEMS = [{ label: "Today", href: "/today", isActive: false }];

/**
 * How the account is protected beyond Google. It lives under `/settings` next to the devices screen, reached
 * from the account menu; the console's side navigation is for the job search, not for the account (ADR-091).
 */
export function SecurityPage() {
    const t = useStrings("security");
    return (
        <AppShell navItems={NAV_ITEMS} title={t("page")} skipLinkLabel="Skip to content" actions={<AccountMenu />}>
            <Stack gap="group-gap">
                <Text variant="body" color="secondary">{t("lead")}</Text>
                <SecondFactorCard />
            </Stack>
        </AppShell>
    );
}
