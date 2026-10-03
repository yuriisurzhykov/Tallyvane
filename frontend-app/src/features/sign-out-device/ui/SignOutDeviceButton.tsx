"use client";

import { useState } from "react";
import { useApi } from "frontend-shared/api";
import { Button } from "frontend-shared/ui/button";
import { Devices, type Device } from "@/entities/device";
import { useStrings } from "@/shared/i18n";

export interface SignOutDeviceButtonProps {
    readonly device: Device;
    /** Another device has been signed out; the list is out of date. */
    readonly onSignedOut: () => void;
    readonly onFailed: () => void;
}

/** Signs out on one device. On this one it is the same as signing out from the account menu: the person leaves. */
export function SignOutDeviceButton({ device, onSignedOut, onFailed }: SignOutDeviceButtonProps) {
    const t = useStrings("devices");
    const api = useApi();
    const [busy, setBusy] = useState(false);

    const signOut = async () => {
        setBusy(true);
        try {
            await new Devices(api).signOut(device);
        } catch {
            setBusy(false);
            onFailed();
            return;
        }
        if (device.isCurrent()) {
            window.location.replace("/login");
            return;
        }
        setBusy(false);
        onSignedOut();
    };

    return (
        <Button tone="neutral" size="sm" loading={busy} onClick={() => void signOut()}>
            {t("signOut")}
        </Button>
    );
}
