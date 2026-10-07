"use client";

import { Badge } from "frontend-shared/ui/badge";
import { Button } from "frontend-shared/ui/button";
import { Callout } from "frontend-shared/ui/callout";
import { EmptyState } from "frontend-shared/ui/empty-state";
import { Row } from "frontend-shared/ui/row";
import { Skeleton } from "frontend-shared/ui/skeleton";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import { VisuallyHidden } from "frontend-shared/ui/visually-hidden";
import { useSecurityActivity, type EntryWords, type SecurityEntry } from "@/entities/security-entry";
import { useStrings } from "@/shared/i18n";

const LOCALE = "en";

/** What the person's account has been through, newest first, a page at a time. */
export function ActivityList() {
    const t = useStrings("activity");
    const { state, showMore, reload } = useSecurityActivity();

    const words: EntryWords = {
        titles: {
            signed_in: t("signedIn"),
            totp_turned_on: t("totpTurnedOn"),
            totp_turned_off: t("totpTurnedOff"),
            recovery_code_spent: t("recoveryCodeSpent"),
            recovery_codes_reissued: t("recoveryCodesReissued"),
            other_devices_signed_out: t("otherDevicesSignedOut"),
            guessing_stopped: t("guessingStopped"),
        },
        withCodesLeft: (title, count) => t("withCodesLeft", { title, count }),
        named: (name, device) => t("named", { name, device }),
        newDevice: t("newDevice"),
        securityChange: t("securityChange"),
        stopped: t("stopped"),
        device: {
            unknownDevice: t("unknownDevice"),
            otherBrowser: t("otherBrowser"),
            unknownSystem: t("unknownSystem"),
            mobile: t("mobile"),
            described: (browser, system) => t("described", { browser, system }),
        },
    };

    if (state.status === "loading") {
        return (
            <Stack gap="stack">
                <VisuallyHidden>{t("loading")}</VisuallyHidden>
                <Skeleton className="h-16 w-full" />
                <Skeleton className="h-16 w-full" />
                <Skeleton className="h-16 w-full" />
            </Stack>
        );
    }

    if (state.status === "failed") {
        return (
            <Stack gap="stack">
                <Callout tone="danger">{t("loadFailed")}</Callout>
                <Row gap="inline">
                    <Button tone="neutral" onClick={reload}>{t("tryAgain")}</Button>
                </Row>
            </Stack>
        );
    }

    const { log, more } = state;

    if (log.isEmpty()) {
        return <EmptyState title={t("empty")} description={t("emptyHint")} />;
    }

    return (
        <Stack gap="stack">
            {log.map((entry, position) => (
                <EntryRow key={position} entry={entry} words={words} />
            ))}
            {more === "failed" ? (
                <Callout tone="attention">{t("moreFailed")}</Callout>
            ) : null}
            {log.hasMore() ? (
                <Row gap="inline">
                    <Button tone="neutral" loading={more === "loading"} onClick={showMore}>
                        {more === "failed" ? t("tryAgain") : t("showMore")}
                    </Button>
                </Row>
            ) : null}
        </Stack>
    );
}

interface EntryRowProps {
    readonly entry: SecurityEntry;
    readonly words: EntryWords;
}

function EntryRow({ entry, words }: EntryRowProps) {
    const badge = entry.badge(words);
    const device = entry.device(words);

    return (
        <Surface variant="elevated" className="p-stack">
            <Stack gap="stack-tight">
                <Row gap="inline" className="flex-wrap items-center">
                    <Text variant="bodyStrong">{entry.title(words)}</Text>
                    {badge === undefined ? null : <Badge tone={entry.tone()}>{badge}</Badge>}
                </Row>
                <Text variant="small" color="secondary">{entry.occurredOn(LOCALE)}</Text>
                {device === undefined ? null : <Text variant="small" color="muted">{device}</Text>}
            </Stack>
        </Surface>
    );
}
