import { Heading } from "frontend-shared/ui/heading";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "@/shared/i18n";
import { AccountMenu } from "@/widgets/account-menu";
import { ProductFrame } from "@/widgets/product-frame";
import { SecondFactorCard } from "@/widgets/second-factor-card";

/**
 * How the account is protected beyond Google. It lives under `/settings` next to the devices screen, reached
 * from the account menu; the console's side navigation is for the job search, not for the account (ADR-091).
 */
export function SecurityPage() {
    const t = useStrings("security");
    return (
        <ProductFrame back account={<AccountMenu />}>
            <Stack gap="group-gap">
                <Heading level={1} variant="title1">{t("page")}</Heading>
                <Text variant="body" color="secondary">{t("lead")}</Text>
                <SecondFactorCard />
            </Stack>
        </ProductFrame>
    );
}
