import { Heading } from "frontend-shared/ui/heading";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { AccountMenu } from "@/widgets/account-menu";
import { ProductFrame } from "@/widgets/product-frame";
import { DeviceList } from "@/widgets/device-list";

/**
 * The devices a person is signed in on. It lives under `/settings` because the settings page that will hold
 * it (second factor, security journal) does not exist yet; no nav item leads here, the account menu does.
 */
export function DevicesPage() {
    return (
        <ProductFrame back account={<AccountMenu />}>
            <Stack gap="group-gap">
                <Heading level={1} variant="title1">Devices</Heading>
                <Text variant="body" color="secondary">
                    Where you are signed in. Sign out on any you no longer use.
                </Text>
                <DeviceList />
            </Stack>
        </ProductFrame>
    );
}
