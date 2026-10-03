"use client";

import { useState } from "react";
import { Badge } from "frontend-shared/ui/badge";
import { Button } from "frontend-shared/ui/button";
import { Callout } from "frontend-shared/ui/callout";
import { Row } from "frontend-shared/ui/row";
import { Skeleton } from "frontend-shared/ui/skeleton";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import { VisuallyHidden } from "frontend-shared/ui/visually-hidden";
import { useDevices, type Device, type DeviceWords } from "@/entities/device";
import { DeviceNameEdit } from "@/features/rename-device";
import { SignOutDeviceButton } from "@/features/sign-out-device";
import { SignOutOtherDevicesButton } from "@/features/sign-out-other-devices";
import { useStrings } from "@/shared/i18n";

const LOCALE = "en";

/** Every device the person is signed in on, each with what can be done to it. */
export function DeviceList() {
    const t = useStrings("devices");
    const { state, refresh } = useDevices();
    const [failed, setFailed] = useState(false);

    const words: DeviceWords = {
        unknownDevice: t("unknownDevice"),
        otherBrowser: t("otherBrowser"),
        unknownSystem: t("unknownSystem"),
        mobile: t("mobile"),
        described: (browser, system) => t("described", { browser, system }),
    };

    const changed = () => {
        setFailed(false);
        refresh();
    };

    if (state.status === "loading") {
        return (
            <Stack gap="stack">
                <VisuallyHidden>{t("loading")}</VisuallyHidden>
                <Skeleton className="h-20 w-full" />
                <Skeleton className="h-20 w-full" />
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

    const now = new Date();
    const others = state.devices.filter((device) => !device.isCurrent()).length;

    return (
        <Stack gap="stack">
            {failed ? <Callout tone="danger">{t("actionFailed")}</Callout> : null}
            {state.stale ? (
                <Callout tone="attention">
                    <Row gap="inline" className="flex-wrap items-center">
                        {t("refreshFailed")}
                        <Button tone="ghost" size="sm" onClick={refresh}>{t("tryAgain")}</Button>
                    </Row>
                </Callout>
            ) : null}
            {state.devices.map((device) => (
                <DeviceRow
                    key={device.key()}
                    device={device}
                    words={words}
                    now={now}
                    onChanged={changed}
                    onFailed={() => { setFailed(true); }}
                />
            ))}
            {others > 0 ? (
                <Row gap="inline">
                    <SignOutOtherDevicesButton others={others} onSignedOut={changed} onFailed={() => { setFailed(true); }} />
                </Row>
            ) : null}
        </Stack>
    );
}

interface DeviceRowProps {
    readonly device: Device;
    readonly words: DeviceWords;
    readonly now: Date;
    readonly onChanged: () => void;
    readonly onFailed: () => void;
}

function DeviceRow({ device, words, now, onChanged, onFailed }: DeviceRowProps) {
    const t = useStrings("devices");
    const description = device.description(words);
    const named = device.givenName() !== "";
    const activity = device.isActiveAt(now)
        ? t("activeNow")
        : t("activeAgo", { when: device.lastActiveAgo(now, LOCALE) });

    return (
        <Surface variant="elevated" className="flex flex-wrap items-start justify-between gap-stack p-stack">
            <Stack gap="stack-tight" className="min-w-0 flex-1">
                <Row gap="inline" className="flex-wrap items-center">
                    <DeviceNameEdit device={device} fallbackLabel={description} onChanged={onChanged} />
                    {device.isCurrent() ? <Badge tone="info">{t("thisDevice")}</Badge> : null}
                </Row>
                {named ? <Text variant="small" color="secondary">{description}</Text> : null}
                <Text variant="small" color="muted" title={t("lastActiveOn", { time: device.lastActiveOn(LOCALE) })}>
                    {t("signedInOn", { date: device.signedInOn(LOCALE) })} · {activity}
                </Text>
            </Stack>
            <SignOutDeviceButton device={device} onSignedOut={onChanged} onFailed={onFailed} />
        </Surface>
    );
}
