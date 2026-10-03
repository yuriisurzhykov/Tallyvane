"use client";

import { useState } from "react";
import { ProblemError, useApi } from "frontend-shared/api";
import { InlineEdit } from "frontend-shared/ui/inline-edit";
import { Input } from "frontend-shared/ui/input";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Text } from "frontend-shared/ui/text";
import { Devices, type Device } from "@/entities/device";
import { useStrings } from "@/shared/i18n";

/**
 * The name goes into the security journal, so it is saved once, when the person commits it (Enter, or leaving
 * the field), and not 400 ms after each keystroke as the rest of the console's fields are (ADR-091). The
 * largest delay a timer takes stands for "never": only a commit saves.
 */
const COMMIT_ONLY = 2_147_483_647;

export interface DeviceNameEditProps {
    readonly device: Device;
    /** What the device is called until the person names it, shown in the name's place. */
    readonly fallbackLabel: string;
    /** The name is saved, or the device is gone; either way the list is out of date. */
    readonly onChanged: () => void;
}

/** The name of a device, where it is shown, which becomes a field when pressed. */
export function DeviceNameEdit({ device, fallbackLabel, onChanged }: DeviceNameEditProps) {
    const t = useStrings("devices");
    const api = useApi();
    const [mistake, setMistake] = useState<string | undefined>(undefined);
    /** A refused name leaves the field showing it; starting the field over shows the name the server has. */
    const [attempt, setAttempt] = useState(0);

    const save = async (name: string) => {
        setMistake(undefined);
        await new Devices(api).rename(device, name);
        onChanged();
    };

    const explain = (failure: unknown) => {
        if (failure instanceof ProblemError && failure.kind() === "not-found") {
            onChanged();
            return;
        }
        const refused = failure instanceof ProblemError && failure.fieldCode("name") === "name-invalid";
        setMistake(refused ? t("nameInvalid") : t("nameFailed"));
        setAttempt((count) => count + 1);
    };

    return (
        <Stack gap="stack-tight">
            <InlineEdit<string>
                key={attempt}
                value={device.givenName()}
                debounceMs={COMMIT_ONLY}
                onSave={save}
                onError={explain}
                editLabel={t("rename")}
                renderValue={(name) => (
                    <Row gap="inline" className="items-baseline">
                        <Text variant="bodyStrong">{name === "" ? fallbackLabel : name}</Text>
                        <Text variant="caption" color="muted">{t("rename")}</Text>
                    </Row>
                )}
                renderEditor={({ value, onChange }) => (
                    <Input
                        value={value}
                        onChange={(event) => { onChange(event.target.value); }}
                        placeholder={t("namePlaceholder")}
                        aria-label={t("rename")}
                        maxLength={60}
                        autoFocus
                    />
                )}
            />
            {mistake !== undefined ? <Text variant="caption" tone="danger">{mistake}</Text> : null}
        </Stack>
    );
}
