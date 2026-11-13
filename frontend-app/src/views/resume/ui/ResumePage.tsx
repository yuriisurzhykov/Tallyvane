import { Heading } from "frontend-shared/ui/heading";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "@/shared/i18n";
import { AccountMenu } from "@/widgets/account-menu";
import { ProductFrame } from "@/widgets/product-frame";
import { ResumeSheet } from "@/widgets/resume-sheet";

export function ResumePage() {
    const t = useStrings("frame");
    const resume = useStrings("resume");
    return (
        <ProductFrame back account={<AccountMenu />}>
            <Stack gap="group-gap">
                <Stack gap="inline-tight">
                    <Heading level={1} variant="title1">{t("resume")}</Heading>
                    <Text variant="body" color="secondary">{resume("lead")}</Text>
                </Stack>
                <ResumeSheet />
            </Stack>
        </ProductFrame>
    );
}
