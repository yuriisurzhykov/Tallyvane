import { Logo } from "frontend-shared/ui/logo";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "@/app/i18n";
import { LandingFaq } from "./LandingFaq";
import { Native, nativeRender } from "./native";

export function LandingPage() {
    const tCommon = useStrings("common");
    const t = useStrings("landing");

    return (
        <Stack gap="section-gap" className="mx-auto min-h-dvh max-w-(--layout-console-max-width) p-screen-padding">
            <Native as="header">
                <Row gap="inline">
                    <Logo text={tCommon("productName")} />
                </Row>
            </Native>
            <Native as="main" className="flex-1">
                <Stack gap="section-gap">
                    <Stack gap="stack">
                        <Text variant="hero" render={nativeRender("h1")}>{tCommon("productName")}</Text>
                        <Text variant="small" color="muted">{t("tagline")}</Text>
                    </Stack>
                    <LandingFaq />
                </Stack>
            </Native>
            <Native as="footer">
                <Stack gap="stack">
                    <Row gap="inline">
                        <Logo text={tCommon("productName")} />
                    </Row>
                    <Text variant="small" color="muted">{t("footerTagline")}</Text>
                </Stack>
            </Native>
        </Stack>
    );
}
