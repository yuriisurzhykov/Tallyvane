import type { ReactNode } from "react";
import { Link } from "frontend-shared/ui/link";
import { Logo } from "frontend-shared/ui/logo";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "@/shared/i18n";

const FOOTER_LINKS = [
    { key: "privacy", href: "https://tallyvane.com/privacy" },
    { key: "terms", href: "https://tallyvane.com/terms" },
    { key: "help", href: "https://tallyvane.com/help" },
] as const;

export interface AuthFrameProps {
    readonly title: string;
    /** One sentence under the title. */
    readonly lead: ReactNode;
    readonly children: ReactNode;
}

/** The page every signed-out screen shares: the mark, one card in the middle, the small links below. */
export function AuthFrame({ title, lead, children }: AuthFrameProps) {
    const t = useStrings("auth");
    return (
        <Stack gap="section-gap" className="min-h-screen bg-surface-inset p-section-gap">
            <Logo text={t("productName")} mark={t("mark")} />
            <Row gap="inline" className="flex-1 justify-center">
                <Surface variant="elevated" className="w-full max-w-(--ds-component-drawer-width) p-section-gap">
                    <Stack gap="group-gap">
                        <Stack gap="stack-tight">
                            <Text variant="title1">{title}</Text>
                            <Text variant="body" color="secondary">{lead}</Text>
                        </Stack>
                        {children}
                    </Stack>
                </Surface>
            </Row>
            <Row gap="group-gap" className="justify-center">
                {FOOTER_LINKS.map((link) => (
                    <Link key={link.key} href={link.href}>{t(link.key)}</Link>
                ))}
            </Row>
        </Stack>
    );
}
