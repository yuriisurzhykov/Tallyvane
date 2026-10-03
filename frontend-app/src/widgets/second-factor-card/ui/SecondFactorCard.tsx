"use client";

import { Badge } from "frontend-shared/ui/badge";
import { Button } from "frontend-shared/ui/button";
import { Callout } from "frontend-shared/ui/callout";
import { Row } from "frontend-shared/ui/row";
import { Skeleton } from "frontend-shared/ui/skeleton";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import { VisuallyHidden } from "frontend-shared/ui/visually-hidden";
import { useSecondFactor, type Standing } from "@/entities/second-factor";
import { DisableTotp } from "@/features/disable-totp";
import { EnableTotp } from "@/features/enable-totp";
import { ReissueRecoveryCodes } from "@/features/reissue-recovery-codes";
import { useStrings } from "@/shared/i18n";

/**
 * The second factor as one card: what is set up, and what can be done about it. It reads the standing from
 * the server and again after every change, and never edits it locally. While recovery codes are on show the
 * standing is not re-read, so the card cannot swap them away before the person has said they saved them.
 */
export function SecondFactorCard() {
    const t = useStrings("security");
    const { state, refresh } = useSecondFactor();

    if (state.status === "loading") {
        return (
            <Stack gap="stack">
                <VisuallyHidden>{t("loading")}</VisuallyHidden>
                <Skeleton className="h-32 w-full" />
            </Stack>
        );
    }

    if (state.status === "failed") {
        return (
            <Stack gap="stack">
                <Callout tone="danger">{t("loadFailed")}</Callout>
                <Row gap="inline">
                    <Button tone="neutral" onClick={refresh}>{t("tryAgain")}</Button>
                </Row>
            </Stack>
        );
    }

    return (
        <Stack gap="stack">
            {state.stale ? (
                <Callout tone="attention">
                    <Row gap="inline" className="flex-wrap items-center">
                        {t("refreshFailed")}
                        <Button tone="ghost" size="sm" onClick={refresh}>{t("tryAgain")}</Button>
                    </Row>
                </Callout>
            ) : null}
            <Surface variant="elevated" className="p-stack">
                <Stack gap="stack">
                    <Row gap="inline" className="flex-wrap items-center">
                        <Text variant="title3">{t("title")}</Text>
                        <StandingBadge standing={state.standing} />
                    </Row>
                    <Body standing={state.standing} onChanged={refresh} />
                </Stack>
            </Surface>
        </Stack>
    );
}

function StandingBadge({ standing }: { readonly standing: Standing }) {
    const t = useStrings("security");
    if (standing.isActive()) {
        return <Badge tone="success">{t("on")}</Badge>;
    }
    if (standing.isRetired()) {
        return <Badge tone="attention">{t("needsSetUp")}</Badge>;
    }
    return <Badge tone="neutral">{t("off")}</Badge>;
}

interface BodyProps {
    readonly standing: Standing;
    readonly onChanged: () => void;
}

function Body({ standing, onChanged }: BodyProps) {
    const t = useStrings("security");

    if (standing.isActive()) {
        return (
            <Stack gap="stack">
                <Text variant="body" color="secondary">{t("activeLead")}</Text>
                <Text variant="small" color="muted">{codesLeftText(t, standing.codesLeft())}</Text>
                <ReissueRecoveryCodes onFinished={onChanged} />
                <DisableTotp onDisabled={onChanged} />
            </Stack>
        );
    }

    if (standing.isRetired()) {
        return (
            <Stack gap="stack">
                <Callout tone="attention">
                    <Stack gap="stack-tight">
                        <Text variant="bodyStrong">{t("retiredTitle")}</Text>
                        <Text variant="small">{t("retiredLead")}</Text>
                        <Text variant="small">{codesLeftText(t, standing.codesLeft())}</Text>
                    </Stack>
                </Callout>
                <EnableTotp again onFinished={onChanged} />
                <DisableTotp onDisabled={onChanged} />
            </Stack>
        );
    }

    return (
        <Stack gap="stack">
            <Text variant="body" color="secondary">{t("offLead")}</Text>
            <EnableTotp again={false} onFinished={onChanged} />
        </Stack>
    );
}

function codesLeftText(t: ReturnType<typeof useStrings<"security">>, left: number): string {
    return left === 0 ? t("noCodesLeft") : left === 1 ? t("oneCodeLeft") : t("manyCodesLeft", { count: left });
}
