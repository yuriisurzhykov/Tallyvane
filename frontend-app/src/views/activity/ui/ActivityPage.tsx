import { Heading } from "frontend-shared/ui/heading";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "@/shared/i18n";
import { AccountMenu } from "@/widgets/account-menu";
import { ProductFrame } from "@/widgets/product-frame";
import { ActivityList } from "@/widgets/activity-list";

/**
 * What has happened to the account's security: sign-ins and changes to the second factor. It lives under
 * `/settings` next to Devices and Security, reached from the account menu (ADR-091, ADR-096).
 */
export function ActivityPage() {
    const t = useStrings("activity");
    return (
        <ProductFrame back account={<AccountMenu />}>
            <Stack gap="group-gap">
                <Heading level={1} variant="title1">{t("page")}</Heading>
                <Text variant="body" color="secondary">{t("lead")}</Text>
                <ActivityList />
            </Stack>
        </ProductFrame>
    );
}
